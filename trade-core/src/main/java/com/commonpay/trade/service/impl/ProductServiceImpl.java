package com.commonpay.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.commonpay.trade.entity.Product;
import com.commonpay.trade.mapper.ProductMapper;
import com.commonpay.trade.service.ProductService;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {
}
