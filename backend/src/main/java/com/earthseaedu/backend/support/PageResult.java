package com.earthseaedu.backend.support;

import java.util.List;

public record PageResult<T>(long total, List<T> items) {
}
