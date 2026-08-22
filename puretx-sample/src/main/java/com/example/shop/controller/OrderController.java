package com.example.shop.controller;

import com.example.shop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/impure")
    public String impure() {
        return "order " + orderService.placeOrder("a book") + " — check the log";
    }

    @PostMapping("/clean")
    public String clean() {
        return "order " + orderService.placeOrderCleanly("a book") + " — nothing in the log";
    }
}
