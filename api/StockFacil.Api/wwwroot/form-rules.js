'use strict';

// Las mismas restricciones del servicio se explican junto al campo del portal.
const formRules = {
    label(value, maximum, required = true) {
        const text = (value || '').trim();
        if (!text) return required ? 'Completa este campo.' : '';
        if (text.length > maximum) return `Admite hasta ${maximum} caracteres.`;
        if (!/[\p{L}\p{N}]/u.test(text) || !/^[\p{L}\p{M}\p{N} .,_+()/&:'"%°-]+$/u.test(text))
            return 'Usa letras, números y signos habituales como guion, punto o paréntesis.';
        return '';
    },
    note(value) {
        if (value.trim().length > 300) return 'Admite hasta 300 caracteres.';
        return /[\x00-\x09\x0B\x0C\x0E-\x1F\x7F-\x9F]/.test(value) ? 'Contiene caracteres no admitidos.' : '';
    },
    units(value, minimum = 0) {
        return !Number.isInteger(value) || value < minimum || value > 1000000
            ? `Ingresa un entero entre ${minimum} y 1 000 000.` : '';
    },
    price(value) {
        return !Number.isFinite(value) || value < 0 || value > 9999999.99 || Math.abs(value - Math.round(value * 100) / 100) > 0.0000001
            ? 'Entre 0 y 9 999 999,99, con hasta dos decimales.' : '';
    }
};

function validateDialog(body) {
    const errors = {};
    document.querySelectorAll('#dialog-fields input, #dialog-fields textarea, #dialog-fields select')
        .forEach(field => field.setCustomValidity(''));
    if (formMode === 'product') {
        errors['f-name'] = formRules.label(body.name, 120);
        errors['f-sku'] = /^[A-Za-z0-9][A-Za-z0-9._-]{0,39}$/.test(body.sku) ? '' : 'Usa de 1 a 40 letras o números, guion, punto o guion bajo; sin espacios.';
        errors['f-brand'] = formRules.label(body.brand, 60, false);
        errors['f-model'] = formRules.label(body.model, 80, false);
        errors['f-price'] = formRules.price(body.price);
        errors['f-stock'] = formRules.units(body.stock);
        errors['f-min'] = formRules.units(body.minimumStock);
        errors['f-category'] = categories.some(c => c.id === body.categoryId) ? '' : 'Selecciona una categoría activa.';
    } else if (formMode === 'category') {
        errors['f-name'] = formRules.label(body.name, 80);
        errors['f-description'] = formRules.note(body.description);
    } else if (formMode === 'movement') {
        const product = products.find(p => p.id === body.productId);
        errors['f-product'] = product ? '' : 'Selecciona un producto publicado.';
        errors['f-type'] = ['IN', 'OUT'].includes(body.type) ? '' : 'Selecciona ingreso o salida.';
        errors['f-quantity'] = formRules.units(body.quantity, 1);
        errors['f-notes'] = formRules.note(body.notes);
        if (product && !errors['f-quantity']) {
            if (body.type === 'OUT' && body.quantity > product.stock)
                errors['f-quantity'] = 'La salida supera las unidades disponibles. Actualiza si el saldo cambió.';
            if (body.type === 'IN' && product.stock + body.quantity > 1000000)
                errors['f-quantity'] = 'El ingreso supera el límite de 1 000 000 de unidades.';
        }
    }
    for (const [id, message] of Object.entries(errors)) $(id).setCustomValidity(message);
    return $('form').reportValidity();
}

let formBusy = false;
let movementUnconfirmed = false;
function setFormBusy(busy) {
    formBusy = busy;
    $('form').querySelectorAll('input, select, textarea, button').forEach(field => field.disabled = busy);
    const submit = $('form').querySelector('[type="submit"]');
    submit.textContent = busy ? 'Guardando…' : 'Guardar';
    if (movementUnconfirmed) submit.disabled = true;
}

async function saveDialog(path, body, method = 'POST') {
    if (formBusy || movementUnconfirmed || !validateDialog(body)) return;
    $('dialog-error').textContent = '';
    setFormBusy(true);
    try {
        await request(path, {method, body: JSON.stringify(body)});
        setFormBusy(false);
        closeDialog();
        await loadAll();
    } catch (error) {
        const rejected = [400, 404, 409, 422].includes(error.status);
        if (formMode === 'movement' && !rejected) {
            movementUnconfirmed = true;
            $('dialog-error').textContent = 'No se recibió confirmación. El movimiento podría haberse guardado. Cierra este formulario y actualiza el historial antes de registrarlo otra vez.';
        } else {
            $('dialog-error').textContent = error.message;
        }
    } finally {
        setFormBusy(false);
    }
}
