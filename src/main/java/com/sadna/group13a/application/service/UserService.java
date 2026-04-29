package com.sadna.group13a.application.service;

import com.sadna.group13a.application.dto.OrderHistoryDTO;
import com.sadna.group13a.application.dto.OrderHistoryItemDTO;
import com.sadna.group13a.application.dto.Result;
import com.sadna.group13a.application.dto.UserDTO;
import com.sadna.group13a.domain.order.IHistoryRepository;
import com.sadna.group13a.domain.order.OrderHistory;
import com.sadna.group13a.domain.user.IUserRepository;
import com.sadna.group13a.domain.user.User;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Application service for managing User Profiles and viewing personal history.
 * Implements UC 2.3 (View Purchase History).
 */
public class UserService {
    private final IUserRepository userRepository;
    private final IHistoryRepository historyRepository;

    public UserService(IUserRepository userRepository, IHistoryRepository historyRepository) {
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * Retrieves the public profile details of a user.
     */
    public Result<UserDTO> getUserProfile(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return Result.failure("User not found");
        }
        User user = userOpt.get();
        UserDTO dto = new UserDTO(user.getId(), user.getUsername(), user.getRole(), user.getState());
        return Result.success(dto);
    }

    /**
     * Retrieves the complete purchase history for a given user.
     * Implements UC 2.3.
     */
    public Result<List<OrderHistoryDTO>> viewOrderHistory(String userId) {
        List<OrderHistory> histories = historyRepository.findByUserId(userId);
        
        List<OrderHistoryDTO> dtos = histories.stream().map(history -> {
            var items = history.getItems().stream().map(i -> new OrderHistoryItemDTO(
                i.getEventId(), i.getEventTitle(), i.getEventDate(), i.getCompanyName(),
                i.getZoneName(), i.getSeatLabel(), i.getPricePaid()
            )).collect(Collectors.toList());
            
            return new OrderHistoryDTO(
                history.getReceiptId(), history.getUserId(), history.getPurchaseDate(),
                history.getTotalPaid(), items
            );
        }).collect(Collectors.toList());
        
        return Result.success(dtos);
    }
}
