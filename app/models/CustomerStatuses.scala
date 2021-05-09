package models

object CustomerStatuses extends Enumeration {
  type Status = Value

  def Active = Value("A")
  def Closed = Value("C")
  def Blocked = Value("B")

}
