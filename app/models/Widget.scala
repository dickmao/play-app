package models

/**
 * Presentation object used for displaying data in a template.
 *
 * Note that it's a good practice to keep the presentation DTO,
 * which are used for reads, distinct from the form processing DTO,
 * which are used for writes.
 */
case class Widget(bedrooms: Int, rentlo: Int, renthi: Int, place: String)
{
}

object Widget {
  val TooDear = "$7000"
}
