package com.example.shop.event;

/** Published once an order has been written, inside the transaction that wrote it. */
public record OrderEvent(long orderId) {
}
