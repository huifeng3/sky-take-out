package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {

    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 历史订单查询
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    PageResult pageQuery4User(int pageNum, int pageSize, Integer status);

    /**
     * 订单详情
     * @param id
     * @return
     */
    OrderVO getOrderDetail(Long id);

    void userCancelById(Long id) throws Exception;

    void repetition(Long id);

    PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO statusCount();

    void adminCancelById(OrdersCancelDTO ordersCancelDTO) throws Exception;

    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception;

    void delivery(Long id);

    void complete(Long id);
}
