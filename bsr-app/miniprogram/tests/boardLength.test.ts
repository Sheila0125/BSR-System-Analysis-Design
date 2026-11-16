import { describe, expect, it } from 'vitest';
import { skiLengthReference, snowboardLengthReference } from '../shared/boardLength';

describe('board length reference', () => {
  it('uses the listed height and skill ranges for skis', () => {
    expect(skiLengthReference('173', 'BEGINNER')).toBe('158–168 cm');
    expect(skiLengthReference('173', 'INTERMEDIATE')).toBe('168–174 cm');
    expect(skiLengthReference('168', 'BEGINNER')).toBe('153–163 cm');
  });

  it('uses the overlapping portion of adjacent snowboard weight bands', () => {
    expect(snowboardLengthReference('68')).toBe('150–153 cm');
    expect(snowboardLengthReference('105')).toBe('165 cm 起');
  });

  it('does not fabricate a number outside the guide or without measurements', () => {
    expect(skiLengthReference(null, 'BEGINNER')).toBe('');
    expect(skiLengthReference('195', 'BEGINNER')).toBe('');
    expect(skiLengthReference('170', 'OTHER')).toBe('');
    expect(snowboardLengthReference('25')).toBe('');
    expect(snowboardLengthReference('')).toBe('');
  });
});
