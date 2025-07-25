package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.baidu.ak}")
    private String ak;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //处理各种业务异常（地址簿为空、购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            //地址簿为空
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

//        //检查用户的收货地址是否超出配送范围
//        checkOutofRange(addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        //查询当前用户的购物车数据
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            //购物车为空
            throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(userId);
        orders.setAddress(addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail());

        orderMapper.insert(orders);

        List<OrderDetail> orderDetailList = new ArrayList<>();

        //向订单明细表插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        //清空当前用户购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderTime(orders.getOrderTime())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

//        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        //为替代微信支付成功后的数据库订单状态更新，多定义一个方法进行修改
        Integer OrderPaidStatus = Orders.PAID;
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;

        //获取订单号
        String orderNumber = ordersPaymentDTO.getOrderNumber();

        log.info("调用updateStatus，用于替换微信支付更新数据库状态的问题");
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, LocalDateTime.now(), orderNumber);

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = getOrderVOList(page);

        return new PageResult(page.getTotal(), list);
    }

    @Override
    public OrderVO getOrderDetail(Long id) {
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);

        List<OrderDetail> orderDetailList = orderDetailMapper.listByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    @Transactional
    @Override
    public void userCancelById(Long id) throws Exception {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orders.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);

        // 订单状态为待接单，需进行退款
        if(orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
//            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    orders.getNumber(), //商户订单号
//                    orders.getNumber(), //商户退款单号
//                    new BigDecimal(0.01), //订单金额
//                    new BigDecimal(0.01) //退款金额
//            );
            //修改订单状态、退款状态
            orders1.setPayStatus(Orders.REFUND);
            log.info("申请退款成功");
        }

        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelReason("用户取消");
        orders1.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders1);
    }

    @Override
    @Transactional
    public void adminCancelById(OrdersCancelDTO ordersCancelDTO) throws Exception {
        Long id = ordersCancelDTO.getId();
        Orders orders = orderMapper.getById(id);
        if(orders == null || !orders.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);

        // 订单状态为待接单，需进行退款
        if(orders.getPayStatus().equals(Orders.PAID)) {
//            //调用微信支付退款接口
//            String refund = weChatPayUtil.refund(
//                    orders.getNumber(), //商户订单号
//                    orders.getNumber(), //商户退款单号
//                    new BigDecimal(0.01), //订单金额
//                    new BigDecimal(0.01) //退款金额
//            );
            //修改订单状态、退款状态
            orders1.setPayStatus(Orders.REFUND);
//            log.info("申请退款：{}", refund);
            log.info("申请退款成功");
        }

        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelReason(ordersCancelDTO.getCancelReason());
        orders1.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders1);
    }

    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        Long id = ordersRejectionDTO.getId();
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);

        // 订单状态为待接单，需进行退款
        if(orders.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
//            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    orders.getNumber(), //商户订单号
//                    orders.getNumber(), //商户退款单号
//                    new BigDecimal(0.01), //订单金额
//                    new BigDecimal(0.01) //退款金额
//            );
            //修改订单状态、退款状态
            orders1.setPayStatus(Orders.REFUND);
            log.info("申请退款成功");
        }
        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelReason(ordersRejectionDTO.getRejectionReason());
        orders1.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders1);
    }

    @Override
    public void delivery(Long id) {
        Orders orders = orderMapper.getById(id);
        if(orders == null || !orders.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(orders1);
    }

    @Override
    public void complete(Long id) {
        Orders orders = orderMapper.getById(id);
        if(orders == null || !orders.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = new Orders();
        orders1.setId(id);
        orders1.setStatus(Orders.COMPLETED);
        orders1.setDeliveryTime(LocalDateTime.now());
        orderMapper.update(orders1);
    }

    @Override
    public void repetition(Long id) {
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> orderDetailList = orderDetailMapper.listByOrderId(id);
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            //创建新的购物车对象
            ShoppingCart shoppingCart = new ShoppingCart();

            //复制订单详情对象属性
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        List<OrderVO> list = getOrderVOList(page);

        return new PageResult(page.getTotal(), list);

    }

    @Override
    public OrderStatisticsVO statusCount() {
        Integer toBeConfirmed = orderMapper.queryByStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.queryByStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.queryByStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;

    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        List<OrderVO> list = new ArrayList<>();

        List<Orders> ordersList = page.getResult();
        if(!CollectionUtils.isEmpty(ordersList)){
            for (Orders orders : ordersList) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                Long orderId = orders.getId();
                List<OrderDetail> orderDetailList = orderDetailMapper.listByOrderId(orderId);

                if(!CollectionUtils.isEmpty(orderDetailList)) {
                    orderVO.setOrderDetailList(orderDetailList);
                    List<String> dishNames = new ArrayList<>();
                    for (OrderDetail orderDetail : orderDetailList) {
                        dishNames.add(orderDetail.getName() + "*" + orderDetail.getNumber() + ";");
                    }
                    orderVO.setOrderDishes(String.join("", dishNames));
                }
                list.add(orderVO);
            }
        }
        return list;
    }

    private void checkOutofRange(String address){
        Map<String, String> map = new HashMap<>();
        map.put("address", address);
        map.put("output", "json");
        map.put("ak", ak);

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
        JSONObject jsonObject = JSONObject.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("店铺地址解析失败");
        }
        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺经纬度坐标
        String shopLngLat = lat + "," + lng;

        map.put("address",address);
        //获取用户收货地址的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);

        jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("收货地址解析失败");
        }

        //数据解析
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户收货地址经纬度坐标
        String userLngLat = lat + "," + lng;

        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);

        jsonObject = JSON.parseObject(json);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }

        //数据解析
        JSONObject result = jsonObject.getJSONObject("result");
        JSONArray jsonArray = (JSONArray) result.get("routes");
        Integer distance = (Integer) ((JSONObject) jsonArray.get(0)).get("distance");

        if(distance > 5000) {
            //配送距离超过5000米
            throw new OrderBusinessException("超出配送范围");
        }

    }

}
