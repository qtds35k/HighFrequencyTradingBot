package trading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderCycle {
	public static final Double GAIN_TARGET = 0.01;
	public static final Double STOP_LOSS = -5.0;
	public static final Integer TRY_BUY_LIMIT = 30;

	HttpHelper myHttpHelper;
	Manager myManager;
	String orderIdBuy;
	String orderIdCancel;
	String orderIdSell;
	String comment;

	private String symbol;
	private Integer qty = 7;
	private Double priceToBuy = 0.0;
	private Double priceBought = 0.0;
	private Double priceToSell = 0.0;
	private Double priceSold = 0.0;

	Double finalGainLoss;
	Double currentGainLoss;

	boolean terminate = false;

	private int state;
	int buyExpireCounter = 0;

	public OrderCycle(HttpHelper myHttpHelper, Manager myManager, String symbol, Double priceToBuy, String comment, boolean marketMaker) throws IOException, InterruptedException {
		this.myHttpHelper = myHttpHelper;
		this.myManager = myManager;
		this.symbol = symbol;
		this.qty = (int) (qty + Math.random() * 20);
		this.priceToBuy = priceToBuy;
		this.comment = comment;
		
		if (marketMaker)
			buyExpireCounter = -Integer.MAX_VALUE;
		
		// place buy in constructor
		this.orderIdBuy = myHttpHelper.placeOrderLimit(symbol, "B", qty, priceToBuy);

		// occupy capital
		myManager.accumulatedOrderMoney += qty * priceToBuy;

		this.orderIdCancel = myHttpHelper.getOrderCancelId();
		if (myHttpHelper.isOrderFilled(orderIdBuy)) {
			placeSellAfterBought();
			state = 2;
		}
		else
			state = 1;
	}

	public void updateOrderStatus() throws IOException, InterruptedException {

		switch (state) {

		// BUY order placed
		// if bought, update bought price
		// if not, check expire counter
		case 1:
			if (myHttpHelper.isOrderFilled(orderIdBuy)) {
				placeSellAfterBought();
				state = 2;
			} else if (buyExpireCounter == TRY_BUY_LIMIT) {
				myHttpHelper.cancelOrder(orderIdCancel, orderIdBuy);
				
				// check if the order is indeed canceled
				if (myHttpHelper.isOrderCancelled(orderIdBuy)) {
					// if yes: restore capital
					// then set terminate to true to end this object in the next loop
					System.out.println("Buy order " + orderIdBuy + " is indeed Cancelled");
					myManager.accumulatedOrderMoney -= qty * priceToBuy;
					terminate = true;
				} else {
					// if not: the order is filled at the last moment
					// place sell accordingly
					placeSellAfterBought();
					state = 2;
				}
			} else
				buyExpireCounter++;
			break;

		// SELL order placed
		// if sold, update info and move on to termination
		// if not, check if stop-loss reached
		case 2:
			if (myHttpHelper.isOrderFilled(orderIdSell)) {
				finalizeAfterSold();
				state = 5;
			} else if (checkStopLoss()) {
				myHttpHelper.cancelOrder(orderIdCancel, orderIdBuy);  // stop-loss reached, cancel sell order
				
				// check if the order is indeed canceled
				if (myHttpHelper.isOrderCancelled(orderIdSell)) {
					// if yes: place new sell order using stop-loss price
					System.out.println("Sell order " + orderIdBuy + " is indeed Cancelled");
					System.out.println("Placing stop-loss sell order...");
					this.priceToSell = myManager.getQuoteBySymbol(symbol);
					this.orderIdSell = myHttpHelper.placeOrderMarket(symbol, "S", qty);
					this.orderIdCancel = myHttpHelper.getOrderCancelId();

					// TODO: test if we need a stop-loss circulation state
					// state = 3;
				} else {
					// if not: the order is filled at the last moment
					// update info and move on to termination
					finalizeAfterSold();
					state = 5;
				}
			}
			break;

		// handle stop-loss sell order
		case 3:
			break;
		
		// order cycle complete, write final Gain/Loss to file
		case 5:
			finalGainLoss = (priceSold - priceBought) * qty;
			
			// update today's GL
			myManager.autoTradeGL += finalGainLoss;
			recordFinalGainLoss();
			terminate = true;
			break;
		}
	}

	private boolean checkStopLoss() {
		return (myManager.getQuoteBySymbol(symbol) - priceBought) * qty < STOP_LOSS;
	}

	private void recordFinalGainLoss() throws IOException {
		String dir = "E:\\notes\\WebBot\\writeTest\\";
		FileWriter fileWriter = new FileWriter(dir + symbol + " GainLossRecord.txt", true);
		PrintWriter printWriter = new PrintWriter(fileWriter);

		// ex: BAC 10 bought sold GL timestamp
		printWriter.printf("%-5s  %4s  %6.2f  %6.2f  %6.2f  %16s  %20s%n", symbol, qty, priceBought, priceSold, finalGainLoss,
				DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").format(LocalDateTime.now()), comment);
		printWriter.close();
	}
	
	private void placeSellAfterBought() throws IOException, InterruptedException {
		// correct capital
		this.priceBought = myHttpHelper.getFilledPrice();
		myManager.accumulatedOrderMoney += qty * (priceBought - priceToBuy);

		// place sell order according to bought price
		this.priceToSell = priceBought + GAIN_TARGET;
		this.orderIdSell = myHttpHelper.placeOrderLimit(symbol, "S", qty, priceToSell);
		this.orderIdCancel = myHttpHelper.getOrderCancelId();
	}
	
	private void finalizeAfterSold() {
		priceSold = myHttpHelper.getFilledPrice();
		myManager.accumulatedOrderMoney -= qty * priceSold;  // restore capital
	}

	public String getSymbol() {
		return symbol;
	}

	public Double getPriceToBuy() {
		return priceToBuy;
	}

	public Double getPriceBought() {
		return priceBought;
	}

	public Double getPriceToSell() {
		return priceToSell;
	}

	public Integer getQty() {
		return qty;
	}

	public Double getPriceSold() {
		return priceSold;
	}

	public int getState() {
		return state;
	}

	protected void setState(int state) {
		this.state = state;
	}

	public String getOrderIdBuy() {
		return orderIdBuy;
	}

	public String getOrderIdCancel() {
		return orderIdCancel;
	}

}
