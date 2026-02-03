package com.orasa.backend.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.orasa.backend.domain.User;

import lombok.Getter;

@Getter
public class CustomUserDetails implements UserDetails{
  
  private final UUID id;
  private final UUID businessId;
  private final String username;
  private final String password;
  private final Collection<? extends GrantedAuthority> authorities;

  public CustomUserDetails(User user) {
    this.id = user.getId();
    this.businessId = user.getBusiness().getId();
    this.username = user.getUsername();
    this.password = user.getPasswordHash();
    this.authorities = List.of(
      new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
    );
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
