package io.ticofab.cm2019.api

case class Event(deviceId: String,
                 eventType: String,
                 amount: Int) {
  override def toString: String = s"Event for device $deviceId, type $eventType, amount: $amount"
}