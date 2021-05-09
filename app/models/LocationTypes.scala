package models

object LocationTypes extends Enumeration {
  type LocationTypes = Value

  val Storage: models.LocationTypes.Value = Value("ST")
  val Shop: models.LocationTypes.Value = Value("SH")

  def isLocationType(l: String) = values.exists(_.toString == l)

}
