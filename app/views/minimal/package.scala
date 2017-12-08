package views

import views.html.helper._

package object minimal {
  implicit val checkboxgroup = new FieldConstructor {
    def apply(elts: FieldElements) = views.html.minimal.checkboxGroupFieldConstructor(elts)
  }
  implicit val price = new FieldConstructor {
    def apply(elts: FieldElements) = views.html.minimal.priceFieldConstructor(elts)
  }

}
