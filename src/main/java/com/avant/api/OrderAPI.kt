package com.avant.api

import com.avant.entity.Person
import com.avant.model.LiqPayModel
import com.avant.model.OrderModel
import com.avant.repo.EventRepo
import com.avant.util.Ret
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import javax.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/api/order")
class OrderAPI(
		val eventRepo: EventRepo,
		val orderModel: OrderModel,
		val liqPayModel: LiqPayModel) {
	
	/**
	 * Creates an order. eventId and dateId are being selected from event entity.
	 * usersCount is count of clients. Mail and comment are optional (you can specify mail in another method).
	 * @param request also must contain following data for EVERY person, followed by number of this client, starts with 0.
	 * Params are: name, phone, document (optional, can be missed), bday (it is birthday), type (Stamdart, Luxe, Econom, as in event's offer) and
	 * class (Student, Children etc., as in event's offer)
	 * Example args for request:
	 * eventId = abc1234
	 * dateId = efg567
	 * usersCount = 2
	 * name0 = Ivan Petrov
	 * name1 = Anna Glushko
	 * phone0 = +380501234567
	 * phone1 = +380678901234
	 * document1 = KB14521243 (! notice: there is no document0 !)
	 * type0 = Econom
	 * class0 = Normal
	 * type1 = Econom
	 * class1 = Student
	 * bday0 = 1975-05-31
	 * bday1 = 1995-12-31
	 * comment = single beds, please
	 */
	@PostMapping("/create")
	fun create(request: HttpServletRequest,
	           @RequestParam eventId: String,
	           @RequestParam dateId: String,
	           @RequestParam usersCount: Int,
	           @RequestParam(required = false) mail: String? = null,
	           @RequestParam(required = false) comment: String? = null): ResponseEntity<*> {
		
		val order = orderModel.createOrder(eventId, dateId, mail, comment)
		val persons = mutableListOf<Person>()
		
		for (n in 0 until usersCount) {
			persons += Person(
					request.getParameter("name$n"),
					request.getParameter("phone$n"),
					request.getParameter("document$n"),
					request.getParameter("type$n"),
					request.getParameter("class$n"),
					LocalDate.parse(request.getParameter("bday$n"))
			)
		}
		
		orderModel.addPersons(order.id, persons)
		
		//		PersonTracker.logCustomers(order)
		//
		//		if (mail != null) {
		//			async { MailSender.sendMail("Вы сделали заказ!", getOrderedDetails(order) + getThanksPayment(order), mail) }
		//		}
		//
		return requestPayment(order.id, order.getDepositPrice(), true)
	}
	
	/**
	 * Sets mail to order. If order already has mail it can be set only with admin's token.
	 * If you can't do this, you'll have 403 returned with specific message.
	 */
	@RequestMapping("/setMail")
	fun setMail(@RequestParam orderId: String, @RequestParam mail: String,
	            @RequestParam(required = false) token: String?): ResponseEntity<*> {
		return Ret.ok(orderModel.setMail(orderId, mail, token))
	}
	
	/**
	 * Request payment form for order.
	 * @param sandbox is sandbox state. this param will be removed on release. If true, funds will not be taken from card.
	 * @param cash is amount to refill. Optional. If not set, deposit price will be set.
	 */
	@GetMapping("/request")
	fun requestPayment(@RequestParam orderId: String,
	                   @RequestParam(required = false) cash: Double?,
	                   @RequestParam(required = false) sandbox: Boolean?): ResponseEntity<*> {
		return Ret.ok(orderModel.requestOrderPayment(orderId, cash, sandbox))
	}
	
	/**
	 * This is for LiqPay API. DO NOT USE THIS!
	 */
	@RequestMapping("/process")
	fun success(req: HttpServletRequest): ResponseEntity<*> {
		liqPayModel.processRequest(req)
		return Ret.ok("Done successfully")
	}
	//
	//	@GetMapping("/payments")
	//	fun all(@RequestParam(value = "limit", required = false) limit: Int?,
	//	        @RequestParam(value = "offset", required = false) offset: Int?): ResponseEntity<*> {
	//		var ret = payRepo.findAll().stream()
	//		if (offset != null) {
	//			ret = ret.skip(offset.toLong())
	//		}
	//		if (limit != null) {
	//			ret = ret.limit(limit.toLong())
	//		}
	//		return ResponseEntity.ok<Array<Any>>(ret.toArray(Payment[]::new  /* Currently unsupported in Kotlin */))
	//	}
	//
	//	@GetMapping("/payment/{id}")
	//	fun payById(@PathVariable("id") id: String): ResponseEntity<*> {
	//		return ResponseEntity.ok(payRepo.findOne(id))
	//	}
	//
	//	@GetMapping("/payment/byorder")
	//	fun paymentByOrder(@RequestParam("orderid") orderid: String): ResponseEntity<*> {
	//		return ResponseEntity.ok(payRepo.findByOrder(orderid))
	//	}
	//
	//	@GetMapping("/orders")
	//	fun orders(@RequestParam(value = "limit", required = false) limit: Int?,
	//	           @RequestParam(value = "offset", required = false) offset: Int?): ResponseEntity<*> {
	//		var ret: Stream<Order> = orderRepo.findAll().stream()
	//		if (offset != null) {
	//			ret = ret.skip(offset.toLong())
	//		}
	//		if (limit != null) {
	//			ret = ret.limit(limit.toLong())
	//		}
	//		return ResponseEntity.ok<Array<Any>>(ret.toArray(Order[]::new  /* Currently unsupported in Kotlin */))
	//	}
	//
	//	@GetMapping("/order/{id}")
	//	fun orderid(@PathVariable("id") id: String): ResponseEntity<*> {
	//		return ResponseEntity.ok<Optional>(orderRepo.findOne(id))
	//	}
	//
	//	@GetMapping("/order/delete/{id}")
	//	fun delete(@PathVariable("id") id: String): ResponseEntity<*> {
	//		orderRepo.delete(id)
	//		return ResponseEntity.ok("OK")
	//	}
	//
	//
	//	// <! ----  MAILS HERE ---- !>
	//
	//
	//	private fun getOrderedDetails(order: Order): String {
	//		val text = StringBuilder()
	//		text.append("Спасибо за заказ. Его номер: ").append(order.id).append("\n<b>")
	//			.append(order.getLinkedEvent().getShortname()).append("</b>")
	//		val integer = AtomicInteger(0)
	//		order.persons.forEach { person ->
	//			text.append("\n\n<b>Персона ").append(integer.incrementAndGet()).append("</b>")
	//
	//			text.append(table(
	//					"ФИО", person.getFullName(),
	//					"Телефон", person.getPhone(),
	//					"Дата рождения", person.getBirthday().toString(),
	//					"Данные документа", person.getDocument(),
	//					"Тип проживания/тура", if (person.getType().equals("no-room")) null else person.getType(),
	//					"Тип клиента", person.getSort()
	//			))
	//		}
	//		if (order.comment != null) {
	//			text.append("\n\nКомментарий к Вашему заказу: ").append(order.comment)
	//		}
	//		return text.toString()
	//	}
	//
	//	private fun trtd(a: String?, b: String?): String {
	//		return "<tr><td>$a</td><td>$b</td></tr>"
	//	}
	//
	//	private fun table(data: Map<String, String>): String {
	//		val ret = StringBuilder("<table>")
	//		data.forEach { key, `val` ->
	//			if (key != null && `val` != null) {
	//				ret.append(trtd(key, `val`))
	//			}
	//		}
	//		return ret.append("</table>").toString()
	//	}
	//
	//	private fun table(vararg data: String): String { //TODO row count
	//		if (data.size % 2 != 0) {
	//			return ""
	//		}
	//		val ret = StringBuilder("<table>")
	//		var i = 0
	//		while (i < data.size) {
	//			if (data[i] != null && data[i + 1] != null) {
	//				ret.append(trtd(data[i], data[i + 1]))
	//			}
	//			i += 2
	//		}
	//		return ret.append("</table>").toString()
	//	}
	//
	//	private fun getThanksPayment(order: Order): String {
	//		return "\nЕсли вы ещё не оплатили заказ, то это можно сделать тут: " +
	//				"https://avant-html.herokuapp.com/event/" + order.getLinkedEvent().getUrl() +
	//				"?surchageOrder=" + order.id
	//	}
	//
	//	private fun getThanksOrder(order: Order): String {
	//		val text = StringBuilder()
	//		text.append("\nВы успешно его забронировали.")
	//		if (order.getFullPrice() !== 0) {
	//			text.append("\nПеред началом Вам нужно будет заплатить до полной стоимости - ").append(order.getFullPrice())
	//		}
	//		text.append(getFooter())
	//		return text.toString()
	//	}
	//
	//	private fun getPaidThanks(order: Order, cash: Double, currency: String): String {
	//		val text = StringBuilder()
	//		text.append("<h4>Спасибо!</h4><br>Вы успешно внесли аванс на ").append(order.getLinkedEvent().getShortname()).append(" на сумму: ")
	//			.append(cash).append(" ").append(currency)
	//			.append("\nНомер вашего бронирования: #").append(order.id).append("\n")
	//
	//		if (order.getDeposit() !== order.getFullPrice()) {
	//			text.append("\n\nПеред началом события Вам останется доплатить до полной стоимости: ")
	//				.append(order.getFullPrice() - order.getDeposit()).append(" ")
	//				.append(order.getLinkedEvent().getCurrency())
	//		}
	//
	//		text.append(getFooter())
	//		return text.toString()
	//	}
	//
	//	private fun getFooter(): String {
	//		return "<br><h4>ВАЖНО</h4>" +
	//				"1. О том, что не забыть взять с собой, телефон координатора и другие подробности появятся на сайте и " +
	//				"у нас в соцсетях за 3 дня до события.<br>" +
	//				"2. Аванс можно вернуть не познее, чем за 10 дней до события. В случае, если осталось меньше 10 дней, " +
	//				"вернуть свой аванс можно отправив вместо себя другого человека.<br>" +
	//				"3. Подробности о получении и правилах действия скидочной клубной карты " +
	//				"<a href='https://avant-html.herokuapp.com'>читайте здесь.</a>"
	//	}
	//
}