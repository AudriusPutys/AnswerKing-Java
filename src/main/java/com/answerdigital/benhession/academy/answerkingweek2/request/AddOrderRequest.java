package com.answerdigital.benhession.academy.answerkingweek2.request;

import javax.validation.constraints.NotBlank;

public record AddOrderRequest(@NotBlank String address) {
}
