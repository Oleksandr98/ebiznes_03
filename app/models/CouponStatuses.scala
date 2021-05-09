package models

object CouponStatuses extends Enumeration {
  type Status = Value

  def Used = Value("U")
  def Cancelled = Value("C")
  def New = Value("N")
}

