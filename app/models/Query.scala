package models

import org.mongodb.morphia.annotations.Entity

/**
 * Presentation object used for displaying data in a template.
 *
 * Note that it's a good practice to keep the presentation DTO,
 * which are used for reads, distinct from the form processing DTO,
 * which are used for writes.
 */
@Entity("searches")
case class Widget(bedrooms: Set[Int], rentlo: Int, renthi: Int, places: Set[String])
{
}

object Widget {
  val TooDear = "$7000"
}