package com.oa.service;

import com.oa.entity.LoginLog;
import com.oa.repository.LoginLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 登录日志（SY-04）。 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogRepository loginLogRepository;

    @Transactional
    public void record(String username, Long userId, boolean success, String failReason,
                       String ip, String userAgent) {
        LoginLog log = new LoginLog();
        log.setUsername(username == null ? "" : username);
        log.setUserId(userId);
        log.setLoginTime(LocalDateTime.now());
        log.setIp(ip == null ? "" : ip);
        log.setUserAgent(userAgent == null || userAgent.length() > 250 ? (userAgent == null ? "" : userAgent.substring(0, 250)) : userAgent);
        log.setSuccess(success);
        log.setFailReason(failReason == null ? "" : failReason);
        loginLogRepository.save(log);
    }

    public Page<LoginLog> page(int page, int size, String username) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "loginTime"));
        if (username == null || username.isBlank()) {
            return loginLogRepository.findAllDesc(pageable);
        }
        return loginLogRepository.findByUsernameContaining(username.trim(), pageable);
    }
}
