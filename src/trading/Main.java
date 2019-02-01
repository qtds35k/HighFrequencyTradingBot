package trading;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main {

	public static void main(String agrs[]) throws IOException, InterruptedException {

		HttpHelper myHttpHelper = new HttpHelper();
		myHttpHelper.login();
		Manager myManager = new Manager(myHttpHelper);
		Map<String, OrderCycle> orderMap = new HashMap<String, OrderCycle>();
		Map<String, OrderCycle> marketMakerOrderMap = new HashMap<String, OrderCycle>();

		final int TIMEOUT_MEAN_SHIFT = 45;
		final int TIMEOUT_UP_TREND = 7;
		final int TIMEOUT_MARKET_MAKER = 10;
		
		int meanShiftCnt = 0;
		int upTrendCnt = 0;
		int marketMakerCnt = 0;

		boolean engageMeanShiftBuying = false;
		boolean engageUpTrendBuying = false;
		boolean engageMarketMaker = true;

		while (true) {
			
			myManager.updatePositionTable(myHttpHelper);
			myManager.recordQuoteHistory();

			// stop trading if too much money is lost today
			if (myManager.checkStopLoss(myHttpHelper))
				break;

			// if capital quota reached, update order status without trading 
			if (myManager.checkCapital()) {
				engageMeanShiftBuying = false;
				engageUpTrendBuying = false;
				engageMarketMaker = false;
			} else {
				engageMeanShiftBuying = true;
				engageUpTrendBuying = true;
				engageMarketMaker = true;
			}

			
			String targetSymbol = "AMD";
			int targetIndex = myManager.getPositionTableIndexBySymbol(targetSymbol);

			// Strategy 1: stable oscillation buying
			// place buy when instant quote is lower than short-term average by some value
			if (engageMeanShiftBuying) {
				if (targetIndex != -1 && myManager.positionTable[targetIndex][4] != null && --meanShiftCnt <= 0) {
					double meanShiftBuyThreashold = 0.01;
					double currentQuote = Double.parseDouble(myManager.positionTable[targetIndex][2]);
					double shortAvg = Double.parseDouble(myManager.positionTable[targetIndex][3]);
					double longAvg = Double.parseDouble(myManager.positionTable[targetIndex][4]);
					
					if (longAvg == shortAvg  && shortAvg - currentQuote >= meanShiftBuyThreashold) {
						println("judging buy threashold: %.3f", currentQuote - shortAvg);
						// stable oscillation BUYs happen here
						OrderCycle newOrder = new OrderCycle(myHttpHelper, myManager, targetSymbol, currentQuote, "stable oscillation", false);
						orderMap.put(newOrder.orderIdBuy, newOrder);
						meanShiftCnt = TIMEOUT_MEAN_SHIFT;
					}
				}
			}

			
			// Strategy 2: up-trend buying
			// place buy when quote's temporarily up-trending
			if (engageUpTrendBuying) {
				if (targetIndex != -1 && myManager.positionTable[targetIndex][4] != null) {
					
					double currentQuote = Double.parseDouble(myManager.positionTable[targetIndex][2]);
					double shortAvg = Double.parseDouble(myManager.positionTable[targetIndex][3]);
					double longAvg = Double.parseDouble(myManager.positionTable[targetIndex][4]);
					
					if (shortAvg > longAvg && currentQuote > shortAvg && --upTrendCnt <= 0) {
							// up-trend BUYs happen here
							OrderCycle newOrder = new OrderCycle(myHttpHelper, myManager, targetSymbol, currentQuote, "up-trend", false);
							orderMap.put(newOrder.orderIdBuy, newOrder);
							println("UP-TREND BUYING");
							java.awt.Toolkit.getDefaultToolkit().beep();
							upTrendCnt = TIMEOUT_UP_TREND;
					}
				}
			}
			

			// Strategy 3: pseudo-market-maker
			// keep a buy order tracing the current bid price
			// if bought, place a sell at some higher price
			if (engageMarketMaker) {
				if ((marketMakerOrderMap.isEmpty() || !hasUnboughtOrders(marketMakerOrderMap)) && --marketMakerCnt <= 0) {
					
					// avoid down-trend buying
					if (myManager.positionTable[targetIndex][4] != null) {
						double currentQuote = Double.parseDouble(myManager.positionTable[targetIndex][2]);
						double shortAvg = Double.parseDouble(myManager.positionTable[targetIndex][3]);
						double longAvg = Double.parseDouble(myManager.positionTable[targetIndex][4]);
						if ((currentQuote < shortAvg && shortAvg < longAvg)) {
							println("Target is in down-trend. Avoid buying.");
						} else {
							// pseudo-market-maker BUYs happen here
							OrderCycle newOrder = new OrderCycle(myHttpHelper, myManager, targetSymbol, myManager.getBidBySymbol(targetSymbol), "market-maker", true);
							marketMakerOrderMap.put(newOrder.orderIdBuy, newOrder);
							println("MARKET MAKER BUYING");
							java.awt.Toolkit.getDefaultToolkit().beep();
							marketMakerCnt = TIMEOUT_MARKET_MAKER;
						}
					}
				}
			}
			
			// iterate through all existing orders
			// remove complete ones and update the others
			handleOrders(orderMap);
			
			// handleMarketOrders returns true if buy order is cancelled due to bid price change
			// thus we adjust the counter to place new buy during next loop
			if (handleMarketMakerOrders(marketMakerOrderMap, myHttpHelper, myManager))
				marketMakerCnt = 0;

			// pause for some 1 sec before looping over
			randomSleep(1);
		}
	}

	// iterate through all existing orders
	// remove complete ones and update the others
	private static void handleOrders(Map<String, OrderCycle> orderMap) throws IOException, InterruptedException {
		for (Map.Entry<String, OrderCycle> entry : orderMap.entrySet()) {
			OrderCycle order = entry.getValue();
			order.updateOrderStatus();  // SELLs may happen here
		}
		
		// remove those with terminate flags from map
		orderMap.entrySet().removeIf(entry-> entry.getValue().terminate);
	}

	
	private static boolean hasUnboughtOrders(Map<String, OrderCycle> orderMap) {
		for (Map.Entry<String, OrderCycle> entry : orderMap.entrySet()) {
			OrderCycle order = entry.getValue();
			if (order.getState() == 1)
				return true;
		}
		return false;
	}
	
	// in addition to simple order handling, this method picks out unbought orders
	// and check if its priceToBuy matches current bid price
	// if not, cancel it (a new one will be placed during the next loop)
	private static boolean handleMarketMakerOrders(Map<String, OrderCycle> marketMakerOrderMap, HttpHelper hh, Manager bigBoss) throws IOException, InterruptedException {
		boolean adjustCounter = false;
		for (Map.Entry<String, OrderCycle> entry : marketMakerOrderMap.entrySet()) {
			OrderCycle order = entry.getValue();
			order.updateOrderStatus();  // SELLs may happen here
			
			String symbol = order.getSymbol();
			Integer qty = order.getQty();
			Double priceToBuy = order.getPriceToBuy();
			Double currenBid = bigBoss.getBidBySymbol(symbol);
			
			if (order.getState() == 1) {
				if (!priceToBuy.equals(currenBid)) {
					if (hh.cancelOrder(order.getOrderIdCancel(), order.getOrderIdBuy())) {
						// 1. the order is still unbought
						// 2. priceToBuy does not match current bid price
						// 3. it's indeed cancelled after the cancel command
						// if all the above are satisfied, terminate the order cycle
						bigBoss.accumulatedOrderMoney -= qty * priceToBuy;
						order.terminate = true;
						adjustCounter = true;
					}
				}
			}
		}

		// remove those with terminate flags from map
		marketMakerOrderMap.entrySet().removeIf(entry-> entry.getValue().terminate);
		
		return adjustCounter;
	}

	private static void println(String msg, Object... args) {
		System.out.println(String.format(msg, args));
	}

	private static void randomSleep(int sec) throws InterruptedException {
		int random_sec = (int) (sec * 1000 + Math.random() * 300);
		println("[PAUSE] " + random_sec + " ms");
		Thread.sleep(random_sec);
	}
}
