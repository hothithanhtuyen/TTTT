'use strict';

const SEAT_PRICE_VND = 20_000;
const BOOKING_FEE_VND = 2_000;
const SEAT_PATTERN = /^[A-F][1-5]$/;
const ORDER_PATTERN = /^[A-F0-9]{32}$/;

function isValidSeatCode(code) {
  return typeof code === 'string' && SEAT_PATTERN.test(code);
}

function amountForSeats(seats) {
  if (!Array.isArray(seats) || seats.length === 0 || seats.some((seat) => !isValidSeatCode(seat))) {
    throw new TypeError('Invalid seats');
  }
  return seats.length * SEAT_PRICE_VND + BOOKING_FEE_VND;
}

module.exports = {
  SEAT_PRICE_VND,
  BOOKING_FEE_VND,
  SEAT_PATTERN,
  ORDER_PATTERN,
  isValidSeatCode,
  amountForSeats,
};
