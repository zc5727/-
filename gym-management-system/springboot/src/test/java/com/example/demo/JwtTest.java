package com.example.demo;

import io.jsonwebtoken.*;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

public class JwtTest {
    private long time = 1000*60*60*24;
    private String signature = "admin";

    @Test
    public void jwt() {
        JwtBuilder jwtBuilder = Jwts.builder();

        String jwtToken =jwtBuilder
                //header
                .setHeaderParam("typ","JWT")
                .setHeaderParam("alg","HS256")
                //payload
                .claim("username","tom")
                .claim("role","admin")
                .setSubject("admin-test")
                .setExpiration(new Date(System.currentTimeMillis()+time))
                .setId(UUID.randomUUID().toString())
                //signature
                .signWith(SignatureAlgorithm.HS256,signature)
                .compact();

        //得到的JWT结果
        System.out.println(jwtToken);
    }

    
    //JWT解码
    @Test
    public void parse() {
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6InRvbSIsInJvbGUiOiJhZG1pbiIsInN1YiI6ImFkbWluLXRlc3QiLCJleHAiOjE2NjIxMzE3ODksImp0aSI6ImZlZmI4Yzc2LWI5YTQtNDk1Yy04MDU1LWRkMzUzMmJiNzc0MWNvbS5leGFtcGxlLmRlbW8uSnd0VGVzdEAxYTQ4MmUzNiJ9.ageAJB0I15gG6TRBKqtyQ8G19-q69AaHlep1s3S60iQ";
        JwtParser jwtParser = Jwts.parser();
        Jws<Claims> claimsJws = jwtParser.setSigningKey(signature).parseClaimsJws(token);
        Claims claims = claimsJws.getBody();
        System.out.println(claims.get("username"));
        System.out.println(claims.get("role"));
        System.out.println(claims.getId());
        System.out.println(claims.getSubject());
        System.out.println(claims.getExpiration());

    }

}