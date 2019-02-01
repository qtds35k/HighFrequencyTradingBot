package trading;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class Manager {
	public static final Double STOP_LOSS_QUOTA = -30.0;
	private static final Double CAPITAL_QUOTA = 3500.0;
	Double balance;
	Double todaysGL;
	Double autoTradeGL;
	Double accumulatedOrderMoney;
	String[][] positionTable;
	int positionTableColumns;
	int positionCount;
	private int idxSymbol = -1;
	private int idxQty = -1;
	private int idxQuote = -1;
	private int idxBid = -1;
	private int idxBidSize = -1;
	
	
	Map<String, LinkedList<Double>> shortStream = new HashMap<String, LinkedList<Double>>();
	Map<String, LinkedList<Double>> longStream = new HashMap<String, LinkedList<Double>>();
	public static final int shortStreamSizeLimit = 15;
	public static final int longStreamSizeLimit = 50;
	Map<String, Double> shortSum = new HashMap<String, Double>();
	Map<String, Double> longSum = new HashMap<String, Double>();
	
	
	public Manager(HttpHelper myHttpHelper) throws IOException, InterruptedException {
		this.balance = myHttpHelper.getBalance();
		this.todaysGL = myHttpHelper.getTodaysGL();
		this.autoTradeGL = 0.0;
		this.accumulatedOrderMoney = 0.0;
		
		Document doc = myHttpHelper.getPositions();

		Elements tableHead = doc.select("#positiontable > thead > tr > th");
		this.positionTableColumns = tableHead.size();
		
		// get correct columns index for customized table
		for (int i = 0; i < positionTableColumns; i ++) {
			switch (tableHead.get(i).text()) {
			case "Symbol":
				idxSymbol = i;
				break;
				
			case "Quantity":
				idxQty = i;
				break;
				
			case "Last":
				idxQuote = i;
				break;
				
			case "Bid":
				idxBid = i;
				break;
				
			case "Bid Size":
				idxBidSize = i;
				break;
			}
		}
		
		if (idxSymbol == -1 || idxQty == -1 || idxQuote == -1 || idxBid == -1 || idxBidSize == -1) {
			print("Please include ");
			if (idxSymbol == -1)
				print("[Symbol] ");
			if (idxQty == -1)
				print("[Quantity] ");
			if (idxQuote == -1)
				print("[Last (stock quote)] ");
			if (idxBid == -1)
				print("[Bid] ");
			if (idxBidSize == -1)
				print("[Bid Size] ");
			println("in your position table column and restart the program");
			println("--- Program Termimated ---");
			System.exit(0);
		}
		
		Elements tableBody = doc.select("#positiontable > tbody > tr > td");
		this.positionCount = tableBody.size() / positionTableColumns;
		
		// table column will be customized as below: 
		// symbol | qty | quote | shortAvg | longAvg | bid | ... | timestamp
		this.positionTable = new String[positionCount][10];
	}

	public boolean checkCapital() {
		println("Order Sum : $%7.2f", accumulatedOrderMoney);
		if (accumulatedOrderMoney > CAPITAL_QUOTA) {
			println("Capital limit reached. Skip trading.");
			return true;
		} else
			return false;
	}

	public boolean checkStopLoss(HttpHelper myHttpHelper) throws IOException, InterruptedException {
		todaysGL = myHttpHelper.getTodaysGL();
		println("Auto-trade GL : " + autoTradeGL.toString());
		
		// TODO: might tidy this fn up later
		if (autoTradeGL < STOP_LOSS_QUOTA) {
			println("Loss limit reached. Stop trading immediately.");
			return true;
		} else
			return false;
	}

	public Double getAcumulatedOrderMoney() {
		return accumulatedOrderMoney;
	}

	public void updatePositionTable(HttpHelper myHttpHelper) throws IOException, InterruptedException {
		Document doc = myHttpHelper.getPositions();
		Elements el = doc.select("#positiontable > tbody > tr > td");

		// should we check if positionCount has changed at this point?

		// update the table as [Symbol, Qty, Quote, ShortAvg, LongAvg, Bid, Bid Size, ..., Time]
		for (int i = 0; i < el.size(); i++) {

			if (i % positionTableColumns == idxSymbol) {
				// Symbol
				String symbolKey_1 = el.get(i).text();
				positionTable[i / positionTableColumns][0] = symbolKey_1;
				
				// create lists for short/long Avg
				if (!shortStream.containsKey(symbolKey_1)) {
					shortStream.put(symbolKey_1, new LinkedList<Double>());
					longStream.put(symbolKey_1, new LinkedList<Double>());
				}
				
				// create maps for short/long Sum (temporary use while calculating Avg)
				if (!shortSum.containsKey(symbolKey_1)) {
					shortSum.put(symbolKey_1, 0.0);
					longSum.put(symbolKey_1, 0.0);
				}
			} else if (i % positionTableColumns == idxQty) {
				// Qty
				positionTable[i / positionTableColumns][1] = el.get(i).text();
				
			} else if (i % positionTableColumns == idxQuote) {
				// Quote
				Double quote = Double.parseDouble(el.get(i).text());
				positionTable[i / positionTableColumns][2] = quote.toString();
				
				// Time
				positionTable[i / positionTableColumns][9] = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
						.format(LocalDateTime.now());

				// ShortAvg
				String symbolKey_2 = positionTable[i / positionTableColumns][0];
				if (shortStream.get(symbolKey_2).size() == shortStreamSizeLimit) {
					positionTable[i / positionTableColumns][3] = Double.toString((shortSum.get(symbolKey_2) / shortStreamSizeLimit));
					shortSum.replace(symbolKey_2, shortSum.get(symbolKey_2) - shortStream.get(symbolKey_2).pollLast());
				}
				shortStream.get(symbolKey_2).push(quote);
				shortSum.replace(symbolKey_2, shortSum.get(symbolKey_2) + quote);

				// LongAvg
				if (longStream.get(symbolKey_2).size() == longStreamSizeLimit) {
					positionTable[i / positionTableColumns][4] = Double.toString((longSum.get(symbolKey_2) / longStreamSizeLimit));
					longSum.replace(symbolKey_2, longSum.get(symbolKey_2) - longStream.get(symbolKey_2).pollLast());
				}
				longStream.get(symbolKey_2).push(quote);
				longSum.replace(symbolKey_2, longSum.get(symbolKey_2) + quote);
				
			} else if (i % positionTableColumns == idxBid) {
				// Bid price
				positionTable[i / positionTableColumns][5] = el.get(i).text();

			} else if (i % positionTableColumns == idxBidSize) {
				// Bid volume
				positionTable[i / positionTableColumns][6] = el.get(i).text();
				
			}
		}
	}

	// returns -1 if not found
	public int getPositionTableIndexBySymbol (String symbol) {
		for (int i = 0; i < positionTable.length; i++)
			if (positionTable[i][0].equalsIgnoreCase(symbol))
				return i;
		return -1;
	}
	// returns -1.0 if not found
	public double getQuoteBySymbol(String symbol) {
		for (int i = 0; i < positionTable.length; i++)
			if (positionTable[i][0].equalsIgnoreCase(symbol))
				return Double.parseDouble(positionTable[i][2]);
		return -1.0;
	}
	// returns -1.0 if not found
	public double getBidBySymbol(String symbol) {
		for (int i = 0; i < positionTable.length; i++)
			if (positionTable[i][0].equalsIgnoreCase(symbol))
				return Double.parseDouble(positionTable[i][5]);
		return -1.0;
	}
	
	public void recordQuoteHistory() throws IOException, InterruptedException {

		for (int i = 0; i < positionCount; i++) {
			String dir = "E:\\notes\\WebBot\\writeTest\\";
			FileWriter fileWriter = new FileWriter(dir + positionTable[i][0] + ".txt", true);
			PrintWriter printWriter = new PrintWriter(fileWriter);

			if (positionTable[i][3] == null)
				printWriter.printf("%-5s  %5s  %6.2f  %6.2f  %6.2f    (%.2f)  %19s%n", positionTable[i][0], positionTable[i][1],
						Double.parseDouble(positionTable[i][2]),
						0.0,
						0.0,
						Double.parseDouble(positionTable[i][5]),
						positionTable[i][9]);
			else if (positionTable[i][4] == null) 
				printWriter.printf("%-5s  %5s  %6.2f  %6.2f  %6.2f    (%.2f)  %19s%n", positionTable[i][0], positionTable[i][1],
						Double.parseDouble(positionTable[i][2]), 
						Double.parseDouble(positionTable[i][3]),
						0.0,
						Double.parseDouble(positionTable[i][5]),
						positionTable[i][9]);
			else
				printWriter.printf("%-5s  %5s  %6.2f  %6.2f  %6.2f    (%.2f)  %19s%n", positionTable[i][0], positionTable[i][1],
						Double.parseDouble(positionTable[i][2]),
						Double.parseDouble(positionTable[i][3]),
						Double.parseDouble(positionTable[i][4]),
						Double.parseDouble(positionTable[i][5]),
						positionTable[i][9]);
			printWriter.close();
			
			// print the target quote in console
			int target_1 = getPositionTableIndexBySymbol("SQQQ");
			int target_2 = getPositionTableIndexBySymbol("AMD");
			int target_3 = getPositionTableIndexBySymbol("TQQQ");
			if (i == target_1 || i == target_2 || i == target_3) {
				if (positionTable[i][4] != null)
					System.out.printf("%-5s  %5s  %6.2f  %6.2f  %6.2f    (%.2f)  %19s%n", positionTable[i][0], positionTable[i][1],
								Double.parseDouble(positionTable[i][2]),
								Double.parseDouble(positionTable[i][3]),
								Double.parseDouble(positionTable[i][4]),
								Double.parseDouble(positionTable[i][5]),
								positionTable[i][9]);
				else if (positionTable[i][3] != null)
					System.out.printf("%-5s  %5s  %6.2f  %6.2f  %6s    (%.2f)  %19s%n", positionTable[i][0], positionTable[i][1],
							Double.parseDouble(positionTable[i][2]),
							Double.parseDouble(positionTable[i][3]),
							"N/A",
							Double.parseDouble(positionTable[i][5]),
							positionTable[i][9]);
				else
					System.out.printf("%-5s  %5s  %6.2f  %6s  %6s    (%.2f)  %19s%n", positionTable[i][0], positionTable[i][1],
							Double.parseDouble(positionTable[i][2]),
							"N/A",
							"N/A",
							Double.parseDouble(positionTable[i][5]),
							positionTable[i][9]);
			}
		}
		
		// alert example
		// the computer will make a series of "ding" sounds when the condition becomes true
		setAlert("AMD", ">", 30.0);
	}
	
	public void setAlert(String targetSymbol, String rule, Double price) throws InterruptedException {
		int idxTrack = getPositionTableIndexBySymbol(targetSymbol);
		if (idxTrack != -1 && positionTable[idxTrack][2] != null) {
			switch (rule) {
			case ">":
				if (Double.parseDouble(positionTable[idxTrack][2]) > price) {
					beep();
					println("--- " + targetSymbol + " " + rule + " " + price + " --- Now: " + positionTable[idxTrack][2]);
				}
				break;
			case ">=":
				if (Double.parseDouble(positionTable[idxTrack][2]) >= price) {
					beep();
					println("--- " + targetSymbol + " " + rule + " " + price + " --- Now: " + positionTable[idxTrack][2]);
				}
				break;
			case "==":
				if (Double.parseDouble(positionTable[idxTrack][2]) == price) {
					beep();
					println("--- " + targetSymbol + " " + rule + " " + price + " --- Now: " + positionTable[idxTrack][2]);
				}
				break;
			case "<=":
				if (Double.parseDouble(positionTable[idxTrack][2]) <= price) {
					beep();
					println("--- " + targetSymbol + " " + rule + " " + price + " --- Now: " + positionTable[idxTrack][2]);
				}
				break;
			case "<":
				if (Double.parseDouble(positionTable[idxTrack][2]) < price) {
					beep();
					println("--- " + targetSymbol + " " + rule + " " + price + " --- Now: " + positionTable[idxTrack][2]);
				}
				break;
			}
		}
	}
	
	private void beep() throws InterruptedException {
		Thread t = new Thread() {
			@Override
			public void run() {
				for (int k = 0; k < 13; k++) {
					java.awt.Toolkit.getDefaultToolkit().beep();
					try {
						sleep(109);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}	
				}
			}
		};
		t.start();  // Start the thread. Call back run() in a new thread
	}
	public String buy(HttpHelper myHttpHelper, String symbol, Integer qty, Double price) throws IOException, InterruptedException {
		String orderId = myHttpHelper.placeOrderLimit(symbol, "B", qty, price);
		return orderId;
	}
	public String sell(HttpHelper myHttpHelper, String symbol, Integer qty, Double price) throws IOException, InterruptedException {
		String orderId = myHttpHelper.placeOrderLimit(symbol, "S", qty, price);
		return orderId;
	}

	private static void print(String msg, Object... args) {
		System.out.print(String.format(msg, args));
	}
	private static void println(String msg, Object... args) {
		System.out.println(String.format(msg, args));
	}
}
