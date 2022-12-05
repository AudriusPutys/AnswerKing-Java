package com.answerdigital.answerking.service;

import com.answerdigital.answerking.exception.custom.OrderCancelledException;
import com.answerdigital.answerking.exception.custom.ProductAlreadyPresentException;
import com.answerdigital.answerking.exception.custom.RetirementException;
import com.answerdigital.answerking.exception.generic.NotFoundException;
import com.answerdigital.answerking.mapper.OrderMapper;
import com.answerdigital.answerking.model.LineItem;
import com.answerdigital.answerking.model.OrderStatus;
import com.answerdigital.answerking.model.Product;
import com.answerdigital.answerking.model.Order;
import com.answerdigital.answerking.repository.OrderRepository;
import com.answerdigital.answerking.request.LineItemRequest;
import com.answerdigital.answerking.request.OrderRequest;
import com.answerdigital.answerking.response.OrderResponse;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    private final ProductService productService;

    private final OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @Autowired
    public OrderService(final OrderRepository orderRepository, final ProductService productService) {
        this.orderRepository = orderRepository;
        this.productService = productService;
    }

    /**
     * Creates an order *
     * @param orderRequest The Order Request
     * @return The created Order
     */
    @Transactional
    public OrderResponse addOrder(final OrderRequest orderRequest) {
        final Order order = new Order();
        addLineItemsToOrder(order, orderRequest.lineItemRequests());
        return convertToResponse(orderRepository.save(order)); // 2nd hit
    }

    /**
     * Finds an Order by a given ID and maps it to an Order Response *
     * @param orderId The Order ID
     * @return The found Order Response
     */
    public OrderResponse getOrderResponseById(final Long orderId) {
        return convertToResponse(getOrderById(orderId));
    }

    /**
     * Finds all the orders within the database *
     * @return A list of all found Orders
     */
    public List<OrderResponse> findAll() {
        return orderRepository.findAll().stream()
            .map(orderMapper::orderToOrderResponse)
            .toList();
    }

    /**
     * Updates an order *
     * @param orderId The ID of the Order
     * @param orderRequest The Order Request
     * @return The updated Order
     */
    @Transactional
    public OrderResponse updateOrder(final Long orderId, final OrderRequest orderRequest) {
        final Order order = getOrderById(orderId);

        if(order.getOrderStatus().equals(OrderStatus.CANCELLED)) {
            throw new OrderCancelledException(
                String.format("The order with ID %d has been cancelled, not possible to update", orderId)
            );
        }

        addLineItemsToOrder(order, orderRequest.lineItemRequests());
        return convertToResponse(orderRepository.save(order));
    }


    private void addLineItemsToOrder(final Order order, List<LineItemRequest> lineItemRequests) {
        List<Long> lineItemProductIds = lineItemRequests.stream()
                .map(LineItemRequest::productId)
                .toList();
        final List<Product> products = productService.findAllProductsInListOfIds(
                lineItemProductIds
        );

        List<Long> foundProductIdsList = products.stream().map(Product::getId).toList();

        List<Long> notFoundProducts = new ArrayList<>(lineItemProductIds);
        notFoundProducts.removeAll(foundProductIdsList);
        if(!notFoundProducts.isEmpty()){
            throw new NotFoundException(String.format("Products with ID's %s do not exist", notFoundProducts.toString()));
        }

        final List<Integer> quantities =
            lineItemRequests.stream()
                .map(LineItemRequest::quantity)
                .toList();

        order.clearLineItems();

        for(int i = 0; i < lineItemRequests.size(); i++) {
            if(products.get(i).isRetired()) {
                throw new RetirementException(String.format("The product with ID %d is retired", products.get(i).getId()));
            }

            order.addLineItem(new LineItem(order, products.get(i), quantities.get(i)));
        }
    }

    /**
     * Helper method which checks if a line item is already present in an Order *
     * @param order The Order to check
     * @param product The Product to check
     */
    private void checkLineItemIsAlreadyPresent(Order order, Product product) {
        final Optional<LineItem> existingLineItem = order.getLineItems()
            .stream()
            .filter(lineItem -> lineItem.getProduct() == product)
            .findFirst();

        if (existingLineItem.isPresent()) {
            throw new ProductAlreadyPresentException(
                String.format("The product with ID %d is already in the order", product.getId())
            );
        }
    }

    /**
     * Marks an Order as cancelled *
     * @param orderId The ID of the Order to cancel
     * @return The order with the status as cancelled
     */
    public OrderResponse cancelOrder(final Long orderId) {
        final Order order = getOrderById(orderId);
        order.setOrderStatus(OrderStatus.CANCELLED);
        return convertToResponse(orderRepository.save(order));
    }

    /**
     * Helper method which converts an Order to an Order Response *
     * @param order The Order instance to convert
     * @return The mapped Order Response
     */
    private OrderResponse convertToResponse(final Order order) {
        return orderMapper.orderToOrderResponse(order);
    }

    /**
     * Helper method which gets a raw Order by an ID *
     * @param orderId The ID of the Order
     * @return The found Order
     */
    private Order getOrderById(final Long orderId) {
        return this.orderRepository
            .findById(orderId)
            .orElseThrow(() -> new NotFoundException(String.format("The order with ID %d does not exist.", orderId)));
    }
}