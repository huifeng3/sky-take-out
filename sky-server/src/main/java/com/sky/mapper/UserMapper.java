package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.xmlbeans.impl.xb.xmlconfig.Extensionconfig;

import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 插入用户数据
     * @param user
     */
    void insert(User user);

    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 根据条件统计用户数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
