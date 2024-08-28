package com.sfelinto.desafio.btgpactual.orderms.service;

import com.sfelinto.desafio.btgpactual.orderms.controller.dto.OrderResponse;
import com.sfelinto.desafio.btgpactual.orderms.entity.OrderEntity;
import com.sfelinto.desafio.btgpactual.orderms.entity.OrderItem;
import com.sfelinto.desafio.btgpactual.orderms.listener.dto.OrderCreatedEvent;
import com.sfelinto.desafio.btgpactual.orderms.repository.OrderRepository;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final MongoTemplate mongoTemplate;

    public OrderService(OrderRepository orderRepository, MongoTemplate mongoTemplate) {
        this.orderRepository = orderRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Page<OrderResponse> findAllOrdersByCustomerId(Long customerId, PageRequest pageRequest) {
        var orders = orderRepository.findAllByCustomerId(customerId, pageRequest);
        return orders.map(OrderResponse::fromEntity);
    }

    public BigDecimal findTotalOnOrdersByCustomerId(Long customerId) {
        var aggregations = newAggregation(
                match(Criteria.where("customerId").is(customerId)),
                group().sum("totalPrice").as("totalPrice")
        );

        var response = mongoTemplate.aggregate(aggregations, "tb_orders", Document.class);

        return new BigDecimal(response.getUniqueMappedResult().get("totalPrice").toString());
    }

    public void save(OrderCreatedEvent orderCreatedEvent) {

        var entity = new OrderEntity();
        entity.setOrderId(orderCreatedEvent.codigoPedido());
        entity.setCustomerId(orderCreatedEvent.codigoCliente());
        entity.setTotalPrice(getTotal(orderCreatedEvent));
        entity.setItems(getOrderItems(orderCreatedEvent));

        orderRepository.save(entity);
    }

    private BigDecimal getTotal(OrderCreatedEvent orderCreatedEvent) {
        return orderCreatedEvent.itens().stream()
                .map(orderItemEvent ->
                        orderItemEvent.preco()
                                .multiply(BigDecimal.valueOf(orderItemEvent.quantidade())))
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
    }


    private static List<OrderItem> getOrderItems(OrderCreatedEvent orderCreatedEvent) {
        return orderCreatedEvent.itens().stream()
                .map(orderItem -> new OrderItem(
                        orderItem.produto(),
                        orderItem.quantidade(),
                        orderItem.preco()
                )).toList();
    }
}
