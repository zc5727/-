package com.gym.utils;

import io.jsonwebtoken.*;

import java.util.Date;
import java.util.UUID;

public class JwtUtil {

    private static long time = 1000*60*60*24;
    private static String signature = "admin";

    public static String createToken() {
        return createToken("admin");
    }

    /**
     * 管理员令牌（带身份）。
     *
     * <p>为什么要带身份：智能体侧需要把"会话归属"绑定到一个<b>服务端可验证</b>的身份，
     * 而不是把前端自称的账号当作事实。原来的令牌只声明 role，无法区分是哪一个管理员／
     * 哪一个会员，智能体就只能在"谁都能自称任何人"的前提下做隔离——那不算隔离。
     *
     * @param adminAccount 管理员账号，写入 claim
     */
    public static String createToken(String adminAccount) {
        JwtBuilder jwtBuilder = Jwts.builder();

        return jwtBuilder
                //header
                .setHeaderParam("typ","JWT")
                .setHeaderParam("alg","HS256")
                //payload
                .claim("username",adminAccount)
                .claim("role","admin")
                .claim("account",adminAccount)
                .setSubject("admin:" + adminAccount)
                .setExpiration(new Date(System.currentTimeMillis()+time))
                .setId(UUID.randomUUID().toString())
                //signature
                .signWith(SignatureAlgorithm.HS256,signature)
                .compact();
    }


    public static String createTokenToMember() {
        return createTokenToMember(null, null);
    }

    /**
     * 会员令牌（带身份）。
     *
     * @param memberNo    会员编号，智能体据此判定"这是哪一位会员"
     * @param memberPhone 会员手机号，仅作辅助展示
     */
    public static String createTokenToMember(Integer memberNo, String memberPhone) {
        JwtBuilder jwtBuilder = Jwts.builder();

        JwtBuilder builder = jwtBuilder
                //header
                .setHeaderParam("typ","JWT")
                .setHeaderParam("alg","HS256")
                //payload
                .claim("username","member")
                .claim("role","member")
                .setSubject(memberNo == null ? "member-test" : "member:" + memberNo)
                .setExpiration(new Date(System.currentTimeMillis()+time))
                .setId(UUID.randomUUID().toString());
        if (memberNo != null) {
            builder = builder.claim("memberNo", memberNo);
        }
        if (memberPhone != null) {
            builder = builder.claim("phone", memberPhone);
        }
        //signature
        return builder.signWith(SignatureAlgorithm.HS256,signature).compact();
    }

    public static boolean checkToken(String token) {
        if (token == null) {
            return false;
        }
        try {
            Jws<Claims> claimsJws = Jwts.parser().setSigningKey(signature).parseClaimsJws(token);
        }catch (Exception e) {
            return false;
        }
        return true;
    }

    /**
     * 解析并校验令牌，返回载荷；令牌无效或已过期返回 null。
     *
     * <p>供智能体侧调用（{@code /agentBridge/identity}）：智能体需要知道
     * "这条会话到底属于谁"，而这件事只能由签发方回答，不能由调用方自称。
     */
    public static Claims parse(String token) {
        if (token == null) {
            return null;
        }
        try {
            return Jwts.parser().setSigningKey(signature).parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }

}
