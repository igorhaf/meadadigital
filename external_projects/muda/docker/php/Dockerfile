FROM php:8.3-fpm-alpine

# System + PHP extension build deps
RUN apk add --no-cache \
        postgresql-dev \
        libzip-dev \
        icu-dev \
        oniguruma-dev \
        bash \
        $PHPIZE_DEPS \
    && docker-php-ext-install \
        pdo \
        pdo_pgsql \
        pgsql \
        bcmath \
        intl \
        zip \
        opcache \
    && apk del $PHPIZE_DEPS

# Composer (for running artisan/composer inside the container)
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

# Match host user so bind-mounted files stay writable
ARG UID=1000
ARG GID=1000
RUN addgroup -g ${GID} app \
    && adduser -u ${UID} -G app -s /bin/bash -D app

WORKDIR /var/www/html
USER app

EXPOSE 9000
CMD ["php-fpm"]
