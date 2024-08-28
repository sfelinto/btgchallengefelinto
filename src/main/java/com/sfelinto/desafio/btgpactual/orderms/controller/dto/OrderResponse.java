package com.sfelinto.desafio.btgpactual.orderms.controller.dto;

import com.sfelinto.desafio.btgpactual.orderms.entity.OrderEntity;

import java.math.BigDecimal;

public record OrderResponse(Long orderId,
                            Long orderCustomerId,
                            BigDecimal total) {

    public static OrderResponse fromEntity(OrderEntity orderEntity) {
        return new OrderResponse(orderEntity.getOrderId(),
                orderEntity.getCustomerId(),
                orderEntity.getTotalPrice());
    }
}
