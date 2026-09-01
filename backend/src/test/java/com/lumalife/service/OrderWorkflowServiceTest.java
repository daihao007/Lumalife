package com.lumalife.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lumalife.domain.Enums.UserRole;
import com.lumalife.domain.Models.Address;
import com.lumalife.domain.Models.User;
import com.lumalife.service.boundary.IdentityServicePort;
import com.lumalife.service.boundary.OrderServicePort;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderWorkflowServiceTest {
  @Test
  void resolvesTheAddressThroughIdentityBeforeCreatingAnOrder() {
    User user = new User(41, "13800000041", "", "远程用户", "", UserRole.USER, null);
    Address address = new Address(401, user.id(), "远程用户", "13800000041", "身份服务地址", true);
    IdentityServicePort identity = mock(IdentityServicePort.class);
    OrderServicePort order = mock(OrderServicePort.class);
    when(identity.addresses(user)).thenReturn(List.of(address));
    when(order.createDeliveryOrdersWithAddress(user, address)).thenReturn(List.of());

    new OrderWorkflowService(order, identity).createDeliveryOrders(user, address.id());

    verify(order).createDeliveryOrdersWithAddress(user, address);
  }
}
