package com.meada.lgpd;

/**
 * Lançada quando o contato pedido (export ou erase) não existe na empresa do tenant.
 * O controller a traduz em 404 contact_not_found. Isola o tenant: pedir um contato de
 * outra empresa cai aqui (o WHERE escopa por companyId), não vaza existência.
 */
public class ContactNotFoundException extends RuntimeException {

    public ContactNotFoundException(String message) {
        super(message);
    }
}
