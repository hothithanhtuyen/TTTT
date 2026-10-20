'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { amountForSeats, isValidSeatCode, ORDER_PATTERN } = require('./domain');

test('authoritative amount uses ticket price plus one booking fee', () => {
  assert.equal(amountForSeats(['A1']), 22_000);
  assert.equal(amountForSeats(['A1', 'B2', 'F5']), 62_000);
});

test('seat codes are restricted to the published six by five map', () => {
  assert.equal(isValidSeatCode('A1'), true);
  assert.equal(isValidSeatCode('F5'), true);
  assert.equal(isValidSeatCode('G1'), false);
  assert.equal(isValidSeatCode('A6'), false);
});

test('order identifiers must be 32 uppercase hexadecimal characters', () => {
  assert.equal(ORDER_PATTERN.test('0123456789ABCDEF0123456789ABCDEF'), true);
  assert.equal(ORDER_PATTERN.test('not-an-order'), false);
});

test('an empty or malformed seat list cannot be priced', () => {
  assert.throws(() => amountForSeats([]), TypeError);
  assert.throws(() => amountForSeats(['A1', 'ZZ']), TypeError);
});
