package com.wedding.checkin.repository;

import com.wedding.checkin.model.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, String> {
}
