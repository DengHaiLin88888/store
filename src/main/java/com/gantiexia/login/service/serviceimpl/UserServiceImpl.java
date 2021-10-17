package com.gantiexia.login.service.serviceimpl;

import cn.hutool.extra.mail.MailUtil;
import com.gantiexia.authcode.entity.AuthCode;
import com.gantiexia.authcode.mapper.AuthCodeMapper;
import com.gantiexia.login.controller.LoginCtrl;
import com.gantiexia.login.entity.User;
import com.gantiexia.login.mapper.LoginMapper;
import com.gantiexia.login.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author GanTieXia
 * @date 2021/10/7 0:55
 */
@Service
public class UserServiceImpl implements UserService {

    // 添加日志打印
    private static final Logger logger = Logger.getLogger(String.valueOf(UserServiceImpl.class));

    @Autowired
    private LoginMapper loginMapper;
    @Autowired
    private AuthCodeMapper authCodeMapper;

    /** 存储账号*/
    private String idNumber;
    /** 存储用户名*/
    private String userName;
    /** 存储图片的名字*/
    private String fileName;
    /** 图片路径*/
    private String fileNamePath;

    /** 初始idNumber,第一个生成的*/
    private final String idNumberStart = "10000001";

    /**
     * 登录
     * @param user
     * @return
     */
    @Override
    public Map<String,String> getUserMessage(User user) {
        // 创建一个Map集合装处理结果
        Map map = new HashMap(2);

        // 查询出系统此账号的账号信息
        List<User> userSysList = loginMapper.getUserMessage(user);

        if(userSysList.size() == 0){
            map.put("code","404");
            map.put("message","此用户不存在");
            return map;
        }

        User userSys = userSysList.get(0);
        // 设置账号信息用于前端获取显示
        idNumber = userSys.getIdNumber();
        userName = userSys.getUserName();
        fileNamePath = userSys.getPersonagePicture();

        // 如果系统中的密码与输入的密码不相等
        if(userSys != null){
            if(userSys.getPassword().equals(user.getPassword())){
                map.put("code","200");
                map.put("message","登陆成功...");
            } else if (!userSys.getPassword().equals(user.getPassword())){
                map.put("code","404");
                map.put("message","登陆失败...");
            }
        } else {
            map.put("code","404");
            map.put("message","登陆失败...");
        }

        return map;
    }

    /**
     * 前端回显账号信息
     *
     * @return
     */
    @Override
    public User showUserMessage() {
        User user = new User();
        // 设置user的回显字段
        user.setIdNumber(idNumber);
        user.setUserName(userName);
        user.setPersonagePicture(fileNamePath);
        return user;
    }

    /**
     * 填充注册账号输入框
     *
     * @return
     */
    @Override
    public String getNextIdNumber() {
        String nextIdNumber = "";
        // 查询用户表
        User user = new User();
        List<User> userList = loginMapper.getUserMessage(user);
        if(userList.size() == 0){
            nextIdNumber = idNumberStart;
        } else if(userList.size() > 0){
            // 当前的ID账号最大值
            String idNumber = loginMapper.getNextIdNumber();
            // 在此基础上+1
            nextIdNumber = String.valueOf(Long.valueOf(idNumber)+1);
        }
        return nextIdNumber;
    }

    /**
     * 发送并存储验证码
     *
     * @param authCode
     * @return
     */
    @Override
    public Map<String,String> checkCode(AuthCode authCode) {
        // 验证码发送结果状态回显
        Map<String,String> map = new HashMap<>();

        StringBuffer authCodes = new StringBuffer();
        for(int j = 0; j< 6; j++){
            authCodes.append((int)((Math.random()*10)))  ;
        }

        // 根据业务需求，每个账号只存在一条验证码记录，所以如果重复点击发送验证码时，提示验证码已发送
        AuthCode authCodePram = new AuthCode();
        List<AuthCode> authCodeList = authCodeMapper.selectAuthCodeInformation(authCodePram);
        // 创建集合装入
        List<AuthCode> alreadySendList = new ArrayList<>();
        for(AuthCode ac : authCodeList){
            if(ac.getIdNumber().equals(authCode.getIdNumber())){
                alreadySendList.add(ac);
            }
        }
        // 当重复点击发送验证码时
        if(alreadySendList.size() > 0){
            map.put("code","500");
            map.put("message","已发送验证码，请勿重复发送");
            return map;
        }

        // 每个邮箱只能绑定一个用户
        User user = new User();
        List<User> userList = loginMapper.getUserMessage(user);
        List<User> sameEmailList = new ArrayList<>();
        for(User userPram : userList){
            if(userPram.getEmail() != null){
                if(userPram.getEmail().equals(authCode.getEmail())){
                    sameEmailList.add(userPram);
                }
            }
        }
        // 当重复绑定一个邮箱时
        if(sameEmailList.size() > 0){
            map.put("code","505");
            map.put("message","此邮箱已被绑定");
            return map;
        }

        // 将验证码、账号、邮箱存入表格中，放入前台校验
        authCode.setAuthCode(String.valueOf(authCodes));
        Date date = new Date();
        authCode.setCrateTime(date);
        authCode.setEmail(authCode.getEmail());
        // 执行插入
        int n = authCodeMapper.insertAuthCode(authCode);
        if(n == 1){
            map.put("code","200");
            map.put("message","验证码发送成功");

            // 当验证码生成成功时才发送验证码
            MailUtil.send(authCode.getEmail(), "Yours杂货铺", "欢迎您的注册！<br>您的注册验证码为:<label style=\"color: red\">"+authCodes+"</label>" +
                    "<br>请妥善保管，防止丢失！<br>如不是您本人操作，请忽略！", true);
            return map;
        } else {
            map.put("code","404");
            map.put("message","验证码发送失败");
            return map;
        }
    }

    /**
     * 头像上传功能
     *
     * @param multipartFile
     * @param user
     * @return
     */
    @Override
    public Map<String, String> saveUploadPicture(MultipartFile multipartFile, User user) {
        // 返回结果参数封装
        Map<String,String> map = new HashMap<>();

        // 此行改为所在工程的对应的上传物理路径
        String uploadAbsolutePath = "D:///storeProject//image";
        // 在对应的image包下按照图片生成日期进行分包
        Date date = new Date();
        // 转换成时间字符串
        SimpleDateFormat sdl = new SimpleDateFormat("yyyyMMdd");
        String nowDay = sdl.format(date);
        // 再将本地路径字符串拼接
        uploadAbsolutePath += "//" + nowDay;
        // 将我们想要保存的路径创建到本地
        File file = new File(uploadAbsolutePath);

        // 如果文件夹不存在
        if (!file.exists() && !file.mkdirs()) {
            map.put("code","404");
            map.put("message","文件上传路径不存在");
            return map;
        }

        //原文件名
        String names = multipartFile.getOriginalFilename();
        // 文件扩展名
        String fileExt = names.substring(names.lastIndexOf(".") + 1).toLowerCase();

        // 文件上传后的新名
        String newName = user.getIdNumber() + "." + fileExt;
        fileName = newName;
        // 文件的绝对路径File
        File uploadFile = new File(uploadAbsolutePath + "/" + newName);

        // 执行文件保存到本地
        try {
            // 将上传的保存到本地指定路径
            FileCopyUtils.copy(multipartFile.getInputStream(), new FileOutputStream(uploadFile));
        } catch (IOException ioException) {
            // IOException代表文件上传的时候出错
            logger.info("图片保存到文件夹中出错！");
        } catch (Exception e) {
            // Exception代表方法出错
            logger.info("文件没有复制到指定的目录下");
        }

        map.put("code","200");
        map.put("message","上传文件成功！");
        return map;
    }

    /**
     * 注册功能
     *
     * @param user
     * @return
     */
    @Override
    public Map<String, String> register(User user) {
        // 返回消息集合
        Map<String,String> map = new HashMap<>();

        // 当多次点击注册按钮时
        User userPram = new User();
        userPram.setIdNumber(user.getIdNumber());
        List<User> userList = loginMapper.getUserMessage(userPram);
        if(userList.size() > 0){
            map.put("code","404");
            map.put("message","您已注册");
            return map;
        }

        // 设置头像路径
        Date date = new Date();
        // 转换成时间字符串
        SimpleDateFormat sdl = new SimpleDateFormat("yyyyMMdd");
        String nowDay = sdl.format(date);
        String personagePicture = "/storeProject/image/" + nowDay + "/" + fileName;
        user.setPersonagePicture(personagePicture);
        // 设置系统时间
        user.setCreateTime(date);
        // 先判断验证码是否正确，获取该账号下的验证码对象
        AuthCode authCode = new AuthCode();
        authCode.setIdNumber(user.getIdNumber());
        List<AuthCode> authCodeList = authCodeMapper.selectAuthCodeInformation(authCode);
        // 如果这条验证记录存在
        if(authCodeList.size() > 0){
            if(authCodeList.get(0).getAuthCode().equals(user.getAuthCode())){
                // 执行插入
                int n = loginMapper.register(user);
                if(n == 1){
                    map.put("code","200");
                    map.put("message","注册成功");
                    return map;
                } else {
                    map.put("code","404");
                    map.put("message","注册失败");
                    return map;
                }
            }else if(!authCodeList.get(0).getAuthCode().equals(user.getAuthCode())){
                map.put("code","404");
                map.put("message","验证码错误");
                return map;
            }
        } else {
            map.put("code","404");
            map.put("message","验证码生成失败，请联系管理员");
            return map;
        }
        return map;
    }
}
