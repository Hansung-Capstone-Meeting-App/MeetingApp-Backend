package com.capston.demo.global.config;

import com.capston.demo.global.security.CustomUserDetails;
import com.capston.demo.global.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Authorization 헤더에서 토큰 추출
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) { //Authorization 헤더가 없거나 "Bearer "로 시작하지 않으면 토큰 검증 건너뜀
            filterChain.doFilter(request, response); //다음 필터로 이동
            return;
        }

        String token = authHeader.substring(7); //"Bearer "제거하고 진행

        try {
            // 토큰 검증
            if (jwtUtil.validateToken(token)) { //토큰 검증 성공 시
                // 토큰에서 이메일, 사용자 ID, 이름 추출
                String email = jwtUtil.extractEmail(token);
                Long userId = jwtUtil.extractUserId(token);
                String name = jwtUtil.extractName(token);

                // SecurityContext에 인증 정보가 없는 경우에만 설정
                if (email != null && userId != null &&
                        name != null &&
                        SecurityContextHolder.getContext().getAuthentication() == null) {

                    // 토큰 정보만으로 UserDetails 생성 (DB 조회 없음)
                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
                    CustomUserDetails userDetails = new CustomUserDetails(
                            userId,
                            email,
                            "",          // 비밀번호는 토큰 인증에서는 사용하지 않음
                            name,
                            authorities
                    );

                    // 인증 객체 생성
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, //사용자 정보
                            null, //비밀번호
                            userDetails.getAuthorities() //권한
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request)); //인증 상세 정보 설정

                    // SecurityContext에 인증 정보 설정
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // 토큰 검증 실패 시 로그만 남기고 계속 진행 (Spring Security가 401 처리)
            logger.error("JWT 토큰 검증 실패: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
