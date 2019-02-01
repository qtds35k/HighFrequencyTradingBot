package trading;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.Connection.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class HttpHelper {
	String urlLogIn = "https://invest.firstrade.com/cgi-bin/login";
	String referer = "https://invest.firstrade.com/cgi-bin/main";
	String agent = "Mozilla/5.0 (Windows NT 6.2; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36";
	Map<String, String> session = new HashMap<String, String>();
	String username;
	String password;
	String pin;
	String account;
	
	private String tempOrderCancelId = "";
	private double tempFilledPrice;

	@SuppressWarnings("resource")
	public HttpHelper() {
		System.out.println("Enter username: ");
		Scanner scanner = new Scanner(System.in);
		this.username = scanner.nextLine();

		System.out.println("Enter password: ");
		scanner = new Scanner(System.in);
		this.password = scanner.nextLine();

		System.out.println("Enter PIN: ");
		scanner = new Scanner(System.in);
		this.pin = scanner.nextLine();

		System.out.println("Enter account: ");
		scanner = new Scanner(System.in);
		this.account = scanner.nextLine();
		
		scanner.close();
	}

	public void login() throws IOException, InterruptedException {
		// get initial login form
		Response loginForm = Jsoup.connect(urlLogIn)
				.userAgent(agent)
				.method(Method.GET)
				.execute();
		
		// initialize session
		session = loginForm.cookies();
		randomSleep(1);
		
		// submit ID and PWD
		Map<String, String> formDataLogin = new HashMap<String, String>();
		formDataLogin.put("redirect", "");
		formDataLogin.put("ft_locale", "en-us");
		formDataLogin.put("login.x", "Log+In");
		formDataLogin.put("username", username);
		formDataLogin.put("password", password);
		formDataLogin.put("destination_page", "acctpositions");
		Connection.Response loginResp = Jsoup.connect(urlLogIn)
				.userAgent(agent)
				.data(formDataLogin)
				.cookies(session)
				.method(Method.POST)
				.execute();
		
		// update session after login
		session.putAll(loginResp.cookies());
		randomSleep(1);

		// submit PIN
		Map<String, String> formDataPin = new HashMap<String, String>();
		formDataPin.put("destination_page", "home");
		formDataPin.put("pin", pin);
		formDataPin.put("pin.x", "OK");
		formDataPin.put("sring", "0");
		formDataPin.put("pin", pin);
		Connection.Response enterPin = Jsoup.connect("https://invest.firstrade.com/cgi-bin/enter_pin?destination_page=home")
				.userAgent(agent)
				.data(formDataPin)
				.referrer(referer)
				.cookies(session)
				.method(Method.POST)
				.execute();

		// check login result
		if(enterPin.parse().select("ul#home_menu").text().contains("SHOUHAO WU"))
			println("Successfully logged in!");
		
		// update session after entering PIN
		session.putAll(enterPin.cookies());
		randomSleep(1);
	}
	
	public Document getPositions() throws IOException, InterruptedException {
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/acctpositions?select_view_id=3")
				.userAgent(agent)
				.data("accountId", account)
				.referrer(referer)
				.cookies(session)
				.method(Method.POST)
				.execute();
		session.putAll(resp.cookies());
		return resp.parse();
	}
	
	public Double getBalance() throws IOException, InterruptedException {
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/acctbalance")
				.userAgent(agent)
				.data("accountId", account)
				.referrer(referer)
				.cookies(session)
				.method(Method.POST)
				.execute();
		session.putAll(resp.cookies());
		randomSleep(1);
		
		Document doc = resp.parse();
		Elements el = doc.select(".total_all > tbody > tr > td");
		Double balance = Double.parseDouble(el.get(0).text().replace("$", "").replace(",", ""));
		println("Balance: " + balance);
		return balance;
	}
	public Double getTodaysGL() throws IOException, InterruptedException {
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/acctbalance")
				.userAgent(agent)
				.data("accountId", account)
				.referrer(referer)
				.cookies(session)
				.method(Method.POST)
				.execute();
		session.putAll(resp.cookies());
		randomSleep(1);
		
		Document doc = resp.parse();
		Elements el = doc.select(".total_all > tbody > tr > td");
		
		Double todaysGL = Double.parseDouble(el.get(1).text().replace("$", "").replace(",", ""));
		println("Today's GL: " + todaysGL);
		return todaysGL;
	}
	
	// place and order and return orderId
	// "B" for buy; "S" for sell
	// if the order is not instantly filled, cancelId will be saved in @tempOrderCancelId
	public String placeOrderLimit(String symbol, String orderType, Integer qty, Double price) throws IOException, InterruptedException {
		// submit buy order
		Map<String, String> formDataOrder = new HashMap<String, String>();
		formDataOrder.put("submiturl", "/cgi-bin/orderbar");
		formDataOrder.put("orderbar_clordid", "");
		formDataOrder.put("orderbar_accountid", "");
		formDataOrder.put("stockorderpage", "yes");
		formDataOrder.put("submitOrders", "1");
		formDataOrder.put("previewOrders", "");
		formDataOrder.put("lotMethod", "1");
		formDataOrder.put("accountType", "2");
		formDataOrder.put("quoteprice", "");
		formDataOrder.put("viewederror", "");
		formDataOrder.put("stocksubmittedcompanyname1", "");
		formDataOrder.put("accountId", account);
		formDataOrder.put("transactionType", orderType);
		formDataOrder.put("quantity", qty.toString());
		formDataOrder.put("symbol", symbol);
		formDataOrder.put("priceType", "2");  // limit: 2
		formDataOrder.put("limitPrice", price.toString());
		formDataOrder.put("duration", "0");
		formDataOrder.put("qualifier", "0");
		formDataOrder.put("cond_symbol0_0", "");
		formDataOrder.put("cond_type0_0", "2");
		formDataOrder.put("cond_compare_type0_0", "2");
		formDataOrder.put("cond_compare_value0_0", "");
		formDataOrder.put("cond_and_or0", "1");
		formDataOrder.put("cond_symbol0_1", "");
		formDataOrder.put("cond_type0_1", "2");
		formDataOrder.put("cond_compare_type0_1", "2");
		formDataOrder.put("cond_compare_value0_1", "");
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/orderbar")
				.userAgent(agent)
				.data(formDataOrder)
				.referrer(referer)
				.cookies(session)
				.method(Method.POST)
				.execute();
		session.putAll(resp.cookies());
		// keep this sleep to get to order status after new order is placed
		randomSleep(1);
		println("===========================");
		print("order placed: ");
		print(orderType.equalsIgnoreCase("B") ? "Buy " : "Sell ");
		println(symbol + " x " + qty + " @ $" + price);
		
		// connect to order list page to check status
		Connection.Response resp_2 = Jsoup.connect("https://invest.firstrade.com/cgi-bin/orderstatus?showopenorder=1&showexeorder=1&showrejectorder=1&showtype=ALL")
				.userAgent(agent)
				.referrer(referer)
				.cookies(session)
				.method(Method.GET)
				.execute();
		session.putAll(resp_2.cookies());
		// randomSleep(1);
		Document doc = resp_2.parse();
		
		// get orderId by checking (symbol, qty, price)
		Elements orderList = doc.select("#order_status > tbody > tr");
		int placedOrderIndex = 0;
		for (int i = 0; i < orderList.size(); i++) {
			Elements orderDetail = orderList.get(i).select("td");
			String orderSymbol = orderDetail.get(3).text();
			String orderQty = orderDetail.get(2).text();
			String orderPrice = orderDetail.get(5).text();
			String orderPlacedType = orderDetail.get(1).text().substring(0, 1);
			
			// locate the order just placed in table
			if (orderSymbol.equalsIgnoreCase(symbol)
					&& orderQty.equalsIgnoreCase(qty.toString())
					&& orderPrice.equalsIgnoreCase(price.toString())
					&& orderPlacedType.equalsIgnoreCase(orderType)) {
				placedOrderIndex = i;
				break;
			}
		}
		String orderId = doc.select("#order_status > tbody > tr").get(placedOrderIndex).id();
		println("order ID: " + orderId);
		
		// TODO code breaks if the order's instantly filled
		// fixed -> to be tested
		String orderStatus = orderList.get(placedOrderIndex).select("td").get(8).text();
		if (orderStatus.contains("Open") || orderStatus.contains("Pending")) {
			this.tempOrderCancelId = orderList.get(placedOrderIndex).select("td.last > div > a.can").get(0).id().replace(account, "");
			println("cancel ID: " + this.tempOrderCancelId);
		}
		else
			this.tempOrderCancelId = "";
		println("===========================");
		return orderId;
	}
	
	public String placeOrderMarket(String symbol, String orderType, Integer qty) throws IOException, InterruptedException {
		// submit buy order
		Map<String, String> formDataOrder = new HashMap<String, String>();
		formDataOrder.put("submiturl", "/cgi-bin/orderbar");
		formDataOrder.put("orderbar_clordid", "");
		formDataOrder.put("orderbar_accountid", "");
		formDataOrder.put("stockorderpage", "yes");
		formDataOrder.put("submitOrders", "1");
		formDataOrder.put("previewOrders", "");
		formDataOrder.put("lotMethod", "1");
		formDataOrder.put("accountType", "2");
		formDataOrder.put("quoteprice", "");
		formDataOrder.put("viewederror", "");
		formDataOrder.put("stocksubmittedcompanyname1", "");
		formDataOrder.put("accountId", account);
		formDataOrder.put("transactionType", orderType);
		formDataOrder.put("quantity", qty.toString());
		formDataOrder.put("symbol", symbol);
		formDataOrder.put("priceType", "1");  // market: 1
		formDataOrder.put("duration", "0");
		formDataOrder.put("qualifier", "0");
		formDataOrder.put("cond_symbol0_0", "");
		formDataOrder.put("cond_type0_0", "2");
		formDataOrder.put("cond_compare_type0_0", "2");
		formDataOrder.put("cond_compare_value0_0", "");
		formDataOrder.put("cond_and_or0", "1");
		formDataOrder.put("cond_symbol0_1", "");
		formDataOrder.put("cond_type0_1", "2");
		formDataOrder.put("cond_compare_type0_1", "2");
		formDataOrder.put("cond_compare_value0_1", "");
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/orderbar")
				.userAgent(agent)
				.data(formDataOrder)
				.referrer(referer)
				.cookies(session)
				.method(Method.POST)
				.execute();
		session.putAll(resp.cookies());
		// keep this sleep to get to order status after new order is placed
		randomSleep(1);
		println("===========================");
		print("order placed: ");
		print(orderType.equalsIgnoreCase("B") ? "Buy " : "Sell ");
		println(symbol + " x " + qty + " @ market price");
		
		// connect to order list page to check status
		Connection.Response resp_2 = Jsoup.connect("https://invest.firstrade.com/cgi-bin/orderstatus?showopenorder=1&showexeorder=1&showrejectorder=1&showtype=ALL")
				.userAgent(agent)
				.referrer(referer)
				.cookies(session)
				.method(Method.GET)
				.execute();
		session.putAll(resp_2.cookies());
		// randomSleep(1);
		Document doc = resp_2.parse();
		
		// get orderId by checking (symbol, qty)
		Elements orderList = doc.select("#order_status > tbody > tr");
		int placedOrderIndex = 0;
		for (int i = 0; i < orderList.size(); i++) {
			Elements orderDetail = orderList.get(i).select("td");
			String orderSymbol = orderDetail.get(3).text();
			String orderQty = orderDetail.get(2).text();
			String orderPlacedType = orderDetail.get(1).text().substring(0, 1);
			
			// locate the order just placed in table
			if (orderSymbol.equalsIgnoreCase(symbol)
					&& orderQty.equalsIgnoreCase(qty.toString())
					&& orderPlacedType.equalsIgnoreCase(orderType)) {
				placedOrderIndex = i;
				break;
			}
		}
		String orderId = doc.select("#order_status > tbody > tr").get(placedOrderIndex).id();
		println("order ID: " + orderId);
		
		// TODO code breaks if the order's instantly filled
		// fixed -> to be tested
		String orderStatus = orderList.get(placedOrderIndex).select("td").get(8).text();
		if (orderStatus.contains("Open") || orderStatus.contains("Pending")) {
			this.tempOrderCancelId = orderList.get(placedOrderIndex).select("td.last > div > a.can").get(0).id().replace(account, "");
			println("cancel ID: " + this.tempOrderCancelId);
		}
		else
			this.tempOrderCancelId = "";
		println("===========================");
		return orderId;
	}

	public String getOrderCancelId () {
		return this.tempOrderCancelId;
	}
	
	public boolean isOrderFilled(String orderId) throws IOException, InterruptedException {
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/orderstatus?showopenorder=1&showexeorder=1&showrejectorder=1&showtype=ALL")
				.userAgent(agent)
				.referrer(referer)
				.cookies(session)
				.method(Method.GET)
				.execute();
		session.putAll(resp.cookies());
		// randomSleep(1);
		Document doc = resp.parse();
		String orderStatus = doc.select("#order_status > tbody > tr#" + orderId + " > td.last > div").get(0).text();
		println("order " + orderId + " is " + orderStatus);
		
		if (orderStatus.contains("Bought") || orderStatus.contains("Sold")) {
			this.tempFilledPrice = Math.round(Double.parseDouble(orderStatus.substring(orderStatus.indexOf("@") + 2)) * 100.0) / 100.0;
			return true;
		}
		else {
			this.tempFilledPrice = 0;
			return false;
		}
	}
	public double getFilledPrice () {
		return this.tempFilledPrice;
	}
	
	public boolean isOrderCancelled(String orderId) throws IOException, InterruptedException {
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/orderstatus?showopenorder=1&showexeorder=1&showrejectorder=1&showtype=ALL")
				.userAgent(agent)
				.referrer(referer)
				.cookies(session)
				.method(Method.GET)
				.execute();
		session.putAll(resp.cookies());
		// randomSleep(1);
		Document doc = resp.parse();
		String orderStatus = doc.select("#order_status > tbody > tr#" + orderId + " > td.last > div").get(0).text();
		
		println("order " + orderId + " is " + orderStatus);
		
		return orderStatus.contains("Cancel");
	}
	
	// cancel an order
	// returns true if it's indeed cancelled
	public boolean cancelOrder(String orderIdCancel, String orderIdBuySell) throws IOException, InterruptedException {
		// cancel an order by orderId
		Map<String, String> formDataCancel = new HashMap<String, String>();
		formDataCancel.put("clordid", orderIdCancel);
		formDataCancel.put("accountid", account);
		Connection.Response resp = Jsoup.connect("https://invest.firstrade.com/cgi-bin/cxlorder")
				.userAgent(agent)
				.data(formDataCancel)
				.referrer(referer)
				.cookies(session)
				.method(Method.POST)
				.execute();
		session.putAll(resp.cookies());
		
		if (isOrderCancelled(orderIdBuySell)) {
			println(orderIdBuySell + " is cancelled");
			return true;
		}
		else {
			println(orderIdBuySell + " is not cancelled");
			return false;
		}
	}

	private static void println(String msg, Object... args) {
		System.out.println(String.format(msg, args));
	}
	private static void print(String msg, Object... args) {
		System.out.print(String.format(msg, args));
	}
	
	// sleeps for some time that is centered at input argument with randomized decimal amplitude
	private static void randomSleep(int sec) throws InterruptedException {
		int random_sec = (int) (sec * 1000 + Math.random() * 300 - 150);
		// let's make it 0.5 sec shorter
		if (random_sec > 500) random_sec -= 500;
		Thread.sleep(random_sec);
	}
}
