package com.devicewatch.auth;

import com.devicewatch.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final SysUserMapper userMapper;
    private final TokenStore tokenStore;

    public AuthController(SysUserMapper userMapper, TokenStore tokenStore) {
        this.userMapper = userMapper;
        this.tokenStore = tokenStore;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) return Result.fail("请输入用户名和密码");
        SysUser user = userMapper.findByUsername(username);
        if (user == null || user.getStatus() != 1
                || !PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash())) {
            return Result.fail("用户名或密码错误");
        }
        String token = tokenStore.create(user);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("realName", user.getRealName());
        data.put("username", user.getUsername());
        return Result.ok(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "X-Token", required = false) String token) {
        tokenStore.remove(token);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(@RequestAttribute("loginUser") SysUser user) {
        Map<String, Object> data = new HashMap<>();
        data.put("realName", user.getRealName());
        data.put("username", user.getUsername());
        return Result.ok(data);
    }
}
