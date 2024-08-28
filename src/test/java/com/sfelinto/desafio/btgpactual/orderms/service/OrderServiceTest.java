package com.sfelinto.desafio.btgpactual.orderms.service;

import com.sfelinto.desafio.btgpactual.orderms.entity.OrderEntity;
import com.sfelinto.desafio.btgpactual.orderms.factory.OrderCreatedEventFactory;
import com.sfelinto.desafio.btgpactual.orderms.factory.OrderEntityFactory;
import com.sfelinto.desafio.btgpactual.orderms.listener.dto.OrderCreatedEvent;
import com.sfelinto.desafio.btgpactual.orderms.repository.OrderRepository;
import org.bson.Document;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    MongoTemplate mongoTemplate;

    @InjectMocks
    private OrderService orderService;

    @Captor
    ArgumentCaptor<OrderEntity> orderEntityCaptor;

    @Captor
    ArgumentCaptor<Aggregation> aggregationCaptor;

    @Nested
    class Save{

        @Test
        void shouldCallRepositorySave() {

            var event = OrderCreatedEventFactory.buildWithOneItem();

            orderService.save(event);

            verify(orderRepository, times(1)).save(any());
        }

        @Test
        void shouldMapEventToEntityWithSuccess() {
            var event = OrderCreatedEventFactory.buildWithOneItem();

            orderService.save(event);

            verify(orderRepository, times(1)).save(orderEntityCaptor.capture());

            var entity = orderEntityCaptor.getValue();

            assertEquals(event.codigoPedido(), entity.getOrderId());
            assertEquals(event.codigoCliente(), entity.getCustomerId());
            assertNotNull(entity.getTotalPrice());
            assertEquals(event.itens().getFirst().produto(), entity.getItems().getFirst().getProductName());
            assertEquals(event.itens().getFirst().quantidade(), entity.getItems().getFirst().getQuantity());
            assertEquals(event.itens().getFirst().preco(), entity.getItems().getFirst().getPrice());
        }

        @Test
        void shouldCalculateOrderTotalWithSuccess() {
            var event = OrderCreatedEventFactory.buildWithTwoItens();
            var totalItem1 = event.itens().getFirst().preco()
                    .multiply(BigDecimal.valueOf(event.itens().getFirst().quantidade()));
            var totalItem2 = event.itens().getLast().preco()
                    .multiply(BigDecimal.valueOf(event.itens().getLast().quantidade()));
            var orderTotal = totalItem1.add(totalItem2);

            orderService.save(event);

            verify(orderRepository, times(1)).save(orderEntityCaptor.capture());

            var entity = orderEntityCaptor.getValue();

            assertNotNull(entity.getTotalPrice());
            assertEquals(orderTotal, entity.getTotalPrice());
        }
    }

    @Nested
    class findAllByCustomerId {

        @Test
        void shouldCallRepository() {
            // ARRANGE
            var customerId = 1L;
            var pageRequest = PageRequest.of(0, 10);
            doReturn(OrderEntityFactory.buildWithPage())
                    .when(orderRepository).findAllByCustomerId(eq(customerId), eq(pageRequest));

            // ACT
            var response = orderService.findAllOrdersByCustomerId(customerId, pageRequest);

            // ASSERT
            verify(orderRepository, times(1))
                    .findAllByCustomerId(eq(customerId), eq(pageRequest));
        }

        @Test
        void shouldMapResponse() {

            var customerId = 1L;
            var pageRequest = PageRequest.of(0, 10);
            var page = OrderEntityFactory.buildWithPage();
            doReturn(page).when(orderRepository).findAllByCustomerId(anyLong(), any());

            var response = orderService.findAllOrdersByCustomerId(customerId, pageRequest);

            assertEquals(page.getTotalPages(), response.getTotalPages());
            assertEquals(page.getTotalElements(), response.getTotalElements());
            assertEquals(page.getSize(), response.getSize());
            assertEquals(page.getNumber(), response.getNumber());

            assertEquals(page.getContent().getFirst().getOrderId(), response.getContent().getFirst().orderId());
            assertEquals(page.getContent().getFirst().getCustomerId(),
                    response.getContent().getFirst().orderCustomerId());
            assertEquals(page.getContent().getFirst().getTotalPrice(), response.getContent().getFirst().total());
        }
    }

    @Nested
    class FindTotalOnOrdersByCustomerId {

        @Test
        void shouldCallMongoTemplate() {
            var customerId = 1L;
            var totalExpected = BigDecimal.valueOf(1);
            var aggregationResult = mock(AggregationResults.class);
            doReturn(new Document("total",  totalExpected)).when(aggregationResult).getUniqueMappedResult();
            doReturn(aggregationResult).when(mongoTemplate)
                    .aggregate(any(Aggregation.class), anyString(), eq(Document.class));

            var total = orderService.findTotalOnOrdersByCustomerId(customerId);

            verify(mongoTemplate, times(1))
                    .aggregate(any(Aggregation.class), anyString(), eq(Document.class));

            assertEquals(totalExpected, total);
        }

        @Test
        void shouldUseCorrectAggregation() {

            var customerId = 1L;
            var totalExpected = BigDecimal.valueOf(1);
            var aggregationResult = mock(AggregationResults.class);
            doReturn(new Document("total",  totalExpected)).when(aggregationResult).getUniqueMappedResult();
            doReturn(aggregationResult).when(mongoTemplate)
                    .aggregate(aggregationCaptor.capture(), anyString(), eq(Document.class));

            orderService.findTotalOnOrdersByCustomerId(customerId);

            var aggregation = aggregationCaptor.getValue();
            var aggregationExpected = newAggregation(
                    match(Criteria.where("customerId").is(customerId)),
                    group().sum("total").as("total")
            );

            assertEquals(aggregationExpected.toString(), aggregation.toString());

        }

        @Test
        void shouldQueryCorrectTable() {
            var customerId = 1L;
            var totalExpected = BigDecimal.valueOf(1);
            var aggregationResult = mock(AggregationResults.class);
            doReturn(new Document("total",  totalExpected)).when(aggregationResult).getUniqueMappedResult();
            doReturn(aggregationResult).when(mongoTemplate)
                    .aggregate(any(Aggregation.class), eq("tb_orders"), eq(Document.class));

            orderService.findTotalOnOrdersByCustomerId(customerId);

            verify(mongoTemplate, times(1))
                    .aggregate(any(Aggregation.class), eq("tb_orders"), eq(Document.class));
        }

    }
}
