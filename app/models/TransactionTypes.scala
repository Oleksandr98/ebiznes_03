package models

object TransactionTypes extends Enumeration {
  type Status = Value

  def Sale = Value("S")
}
