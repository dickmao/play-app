package models

import java.util.HashSet
import org.mongodb.morphia.annotations.Entity

/**
 * Presentation object used for displaying data in a template.
 *
 * Note that it's a good practice to keep the presentation DTO,
 * which are used for reads, distinct from the form processing DTO,
 * which are used for writes.
 */

@Entity("searches")
case class Widget(bedrooms: java.util.Set[java.lang.Integer], rentlo: java.lang.Integer, renthi: java.lang.Integer, places: java.util.Set[String])
{
  private def this() = this(new java.util.HashSet(), 0, 0, new java.util.HashSet())
}

object Widget {
  val TooDear = "$7000"
}
