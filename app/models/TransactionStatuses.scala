package models

object TransactionStatuses extends Enumeration {
  type Status = Value

  def Complete = Value("C")
  def Reversed = Value("R")
}

