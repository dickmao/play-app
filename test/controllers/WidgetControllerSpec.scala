package controllers
import org.scalatestplus.play._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test._
import play.api.test.Helpers._

/**
 * Tests functionality of the controller.
 *
 * https://www.playframework.com/documentation/latest/ScalaFunctionalTestingWithScalaTest
 */
class WidgetControllerSpec extends PlaySpec with GuiceOneAppPerSuite {

  "Post Request" should {
    "should result in redirect" in {
      val request = FakeRequest("POST", "/widgets").withFormUrlEncodedBody("checkbeds[]" -> "0", "rentlo" -> "$100", "renthi" -> "$200", "autocomplete" -> "Astoria, Queens")

      val Some(result) = route(app, request)
      status(result) mustEqual SEE_OTHER
    }
  }

}
