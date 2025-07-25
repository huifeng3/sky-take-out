package com.sky.controller.admin;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/order")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/conditionSearch")
    public Result pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("查询订单");
        PageResult pageResult = orderService.pageQuery(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("details/{id}")
    public Result details(@PathVariable Long id) {
        log.info("查询订单详情");
        OrderVO orderVO = orderService.getOrderDetail(id);
        return Result.success(orderVO);
    }

    @GetMapping("/statistics")
    public Result statusCount(){
        log.info("各个状态订单数量统计");
        OrderStatisticsVO orderStatisticsVO = orderService.statusCount();
        return Result.success(orderStatisticsVO);
    }

    @PutMapping("/cancel")
    public Result cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) throws Exception {
        log.info("取消订单：{}", ordersCancelDTO);
        orderService.adminCancelById(ordersCancelDTO);
        return Result.success();
    }

    @PutMapping("/confirm")
    public Result confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO){
        log.info("确认订单：{}", ordersConfirmDTO);
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }

    @PutMapping("/rejection")
    public Result rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        log.info("拒单：{}", ordersRejectionDTO);
        orderService.rejection(ordersRejectionDTO);
        return Result.success();
    }

    @PutMapping("/delivery/{id}")
    public Result delivery(@PathVariable Long id){
        log.info("订单派送：{}", id);
        orderService.delivery(id);
        return Result.success();
    }

    @PutMapping("/complete/{id}")
    public Result complete(@PathVariable Long id){
        log.info("订单完成：{}", id);
        orderService.complete(id);
        return Result.success();
    }

}
