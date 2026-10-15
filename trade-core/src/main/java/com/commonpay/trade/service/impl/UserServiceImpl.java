package com.commonpay.trade.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.commonpay.trade.entity.User;
import com.commonpay.trade.mapper.UserMapper;
import com.commonpay.trade.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
