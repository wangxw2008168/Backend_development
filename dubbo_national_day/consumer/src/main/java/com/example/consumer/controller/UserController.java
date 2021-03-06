package com.example.consumer.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.example.consumer.config.annotation.AnnotationCurrentUser;
import com.example.consumer.config.annotation.AnnotationLoginRequired;
import com.example.consumer.contants.UserContants;
import com.example.consumer.util.RedisUtils;
import com.example.consumer.util.result.ReturnResult;
import com.example.consumer.util.result.ReturnResultContants;
import com.example.consumer.util.result.ReturnResultUtils;
import com.example.consumer.vo.ResultUserListVo;
import com.example.consumer.vo.ResultUserVo;
import com.example.consumer.vo.UserVo;
import com.example.provider.dto.User;
import com.example.provider.service.UserService;
import com.google.common.collect.Lists;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * **********************************************************
 *
 * @Project:
 * @Author : Gavincoder
 * @Mail : xunyegege@gmail.com
 * @Github : https://github.com/xunyegege
 * @ver : version 1.0
 * @Date : 2019-10-02 15:59
 * @description:
 ************************************************************/

@Slf4j
@Api(tags = "用户接口")
@RestController
@RequestMapping(value = "/v1")
public class UserController {


    @Reference
    private UserService userService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private  HttpServletRequest request;

    @ApiOperation(value = "登录")
    @PostMapping(value = "/login")
    public ReturnResult login(@Valid UserVo userVo) {


        log.info("测试@Slf4j");

//        if (log.isDebugEnabled()) {
//            log.debug("Processing trade with id: " + "id" + " and symbol: " + "symbol");
//        }

        //1.验证用户信息

        User user = userService.login(userVo.getUserName(), userVo.getPassword());

        if (null != user) {

            //2.生成token,jsonStr set到redis中,并设置60秒过期时间

            String token = request.getSession().getId();

            BeanUtils.copyProperties(user, userVo);
            String jsonString = JSONObject.toJSONString(userVo);
            boolean set = redisUtils.set(UserContants.LOGIN_NAME_SPACE + token, jsonString, 60);
            if (set) {

                return ReturnResultUtils.returnSuccess(token);
            }

        }


        return ReturnResultUtils.returnFail(ReturnResultContants.LOGIN_WRONG, ReturnResultContants.MSG_WRONG_LOGIN);


    }


    @ApiOperation(value = "注册")
    @PostMapping(value = "/register")
    public ReturnResult register(@Valid UserVo userVo) {
        //1.判断用户名是否被注册
        Object obj = redisUtils.get(UserContants.REGISTER_NAME_SPACE + userVo.getUserName());

        if (null == obj) {

            //2.插入数据库
            User user = new User();
            BeanUtils.copyProperties(userVo, user);

            user.setBTime(new Date());

            int registerResult = userService.register(user);

            if (registerResult == 1) {

                //3.插入redis,完善注册库数据
                redisUtils.set(UserContants.REGISTER_NAME_SPACE + userVo.getUserName(), 1);

                //4.获取token,用1作为value,插入redis,(验证登录用)
                String token = request.getSession().getId();

                boolean set = redisUtils.set(UserContants.LOGIN_NAME_SPACE + token,
                                             JSONObject.toJSONString(userVo), 300);

                //5.返回token
                if (set) {
                    return ReturnResultUtils.returnSuccess(token);
                }

            }
        } else {

            return ReturnResultUtils.returnFail(ReturnResultContants.REGISTER_ALREADY_EXIST,
                                                ReturnResultContants.MSG_REGISTER_ALREADY_EXIST);
        }

        return ReturnResultUtils.returnFail(ReturnResultContants.REGISTER_WRONG,
                                            ReturnResultContants.MSG_WRONG_REGISTER);
    }


    @AnnotationLoginRequired
    @ApiOperation(value = "获取用户")
    @GetMapping(value = "/findUser")
    public ReturnResult findUser(@Valid UserVo userVo) {

        //对象转换
        User user = new User();
        BeanUtils.copyProperties(userVo, user);

        user.setStart((userVo.getStartPage() - 1) * userVo.getPageSize());
        user.setOffset(userVo.getPageSize());

        List<User> userList = userService.findUser(user);

        ArrayList<ResultUserVo> resultList = Lists.newArrayList();


        userList.forEach(userObj -> {
            ResultUserVo resultUserVo = new ResultUserVo();
            BeanUtils.copyProperties(userObj, resultUserVo);
            resultList.add(resultUserVo);
        });


        ResultUserListVo resultUserListVo = new ResultUserListVo();

        resultUserListVo.setList(resultList);

        int totalSize = userService.countToatlSize(user);

        resultUserListVo.setTotalSize(totalSize);
        resultUserListVo.setNowPage(userVo.getStartPage());
        resultUserListVo.setPageSize(userVo.getPageSize());


        return ReturnResultUtils.returnSuccess(resultUserListVo);
    }


    @AnnotationLoginRequired
    @ApiOperation(value = "删除用户")
    @DeleteMapping(value = "/delUser")
    public ReturnResult delUser(@AnnotationCurrentUser UserVo userVo) {


        int result = userService.delUser(userVo.getUserName());
        if (result == 1) {

            return ReturnResultUtils.returnSuccess();
        }

        return ReturnResultUtils.returnFail(ReturnResultContants.DEL_WRONG, ReturnResultContants.MSG_DEL_WRONG);

    }




}
