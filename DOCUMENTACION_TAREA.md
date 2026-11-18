# Documento de Entrega: Microservicio de Notificaciones (Notification Service)

**Materia / Proyecto:** Arquitectura de Software / Sistemas Distribuidos  
**Sistema:** Sistema Distribuido Wallet  
**Componente:** Microservicio de Notificaciones (`notification-service`)  

---

## 1. ¿Qué hace este microservicio?

El **Microservicio de Notificaciones** se encarga de una tarea muy puntual pero crítica: **enviar correos electrónicos automáticos a los usuarios** cada vez que ocurre un evento importante en el sistema (por ejemplo, cuando un usuario se registra o actualiza su cuenta).

### ¿Por qué se hizo como un servicio separado?
Normalmente, si el servicio de usuarios enviara el correo directamente por sí mismo, la página se quedaría "cargando" esperando que el servidor de correos responda. Si el servicio de correos falla o tarda 5 segundos, la aplicación se vuelve lenta o da error.

Al separarlo:
1. El usuario se registra rápidamente.
2. Se deja un "mensaje" en una fila de espera (RabbitMQ).
3. Este microservicio toma el mensaje con calma en segundo plano y despacha el correo mediante **Brevo**.
4. Si el correo falla, la cuenta del usuario no se borra ni se afecta el resto del sistema.

---

## 2. ¿Cómo funciona la arquitectura? (En palabras simples)

El flujo sigue un modelo **asíncrono y guiado por eventos**:

1. **El disparador:** El servicio de usuarios (`users-management`) registra a una persona y publica el evento `user.created` en RabbitMQ.
2. **El buzón (Broker):** RabbitMQ (en la nube con CloudAMQP) recibe el mensaje y lo guarda en la cola `notification.events.queue`.
3. **El consumidor:** Nuestro microservicio (`notification-service`) está escuchando esa cola. En cuanto ve un mensaje nuevo, lo toma, extrae el nombre y correo del usuario.
4. **El cartero (API externa):** El microservicio se comunica con la API de **Brevo** (Sendinblue) para enviar el correo con la plantilla correspondiente (*"Bienvenido a nuestra plataforma"*).
5. **Monitoreo:** El servicio cuenta con una ruta `/health` para que Render y nosotros sepamos en cualquier momento si está vivo y funcionando.

---

## 3. Tecnologías utilizadas

- **Java 25 & Spring Boot:** Base del microservicio para la lógica y conexión con RabbitMQ.
- **Spring AMQP (RabbitMQ):** Librería para suscribirse y consumir mensajes de la cola.
- **CloudAMQP:** Servicio en la nube que hospeda el broker RabbitMQ.
- **Brevo API (Sendinblue):** Plataforma para el despacho real de correos transaccionales.
- **Docker:** Empaqueta toda la aplicación con sus dependencias para que funcione igual en cualquier máquina.
- **Render:** Plataforma en la nube donde está desplegado el contenedor Docker.

---

## 4. Configuración del Entorno (`.env` / Variables)

Para que el servicio pueda operar, requiere las siguientes variables de configuración en Render:

| Variable | ¿Para qué sirve? | Ejemplo / Valor |
| :--- | :--- | :--- |
| `PORT` | Puerto HTTP donde corre el servicio | `8080` (Asignado por Render) |
| `RABBITMQ_URL` | Dirección de conexión con usuario y contraseña a CloudAMQP | `amqps://usuario:clave@host/vhost` |
| `BREVO_API_KEY` | Clave secreta para autorizar envíos en Brevo | `xkeysib-xxxx...` |
| `BREVO_FROM_ADDRESS` | Correo verificado del remitente en Brevo | `tu_correo@gmail.com` |
| `BREVO_FROM_NAME` | Nombre que ve el usuario como remitente | `Notification Service` |

---

## 5. Pruebas y Evidencias de Funcionamiento

A continuación se presentan las evidencias del despliegue y funcionamiento del microservicio:

### 📸 Evidencia 1: Variables de Entorno Configuradas en Render
Muestra la configuración en la nube de las credenciales de Brevo y la conexión a CloudAMQP.

> 🖼️ **[PEGAR AQUÍ CAPTURA 1: Dashboard de Render -> Pestaña "Environment" con las variables configuradas]**

---

### 📸 Evidencia 2: Verificación de Salud del Microservicio (`/health`)
Prueba de funcionamiento del endpoint `/health` respondiendo con código HTTP 200 y estado `UP`.

```bash
curl https://notification-microservice-j779.onrender.com/health
# Respuesta: {"status":"UP"}
```

> 🖼️ **[PEGAR AQUÍ CAPTURA 2: Terminal o navegador mostrando la respuesta {"status":"UP"}]**

---

### 📸 Evidencia 3: Cola de Mensajes en CloudAMQP (RabbitMQ)
Muestra la cola `notification.events.queue` creada y enlazada al exchange `domain.events`.

> 🖼️ **[PEGAR AQUÍ CAPTURA 3: Consola de CloudAMQP mostrando la cola "notification.events.queue" activa y conectada]**

---

### 📸 Evidencia 4: Logs del Servicio Procesando y Despachando Correos
Muestra el registro en consola donde el servicio confirma la recepción del evento y la entrega exitosa a Brevo:  
`INFO ... [BrevoEmailSenderAdapter] : Email notification accepted by Brevo`

> 🖼️ **[PEGAR AQUÍ CAPTURA 4: Pestaña "Logs" en Render mostrando los mensajes "Email notification accepted by Brevo"]**

---

### 📸 Evidencia 5: Correo Recibido en la Bandeja de Entrada
Muestra el correo electrónico real recibido por el usuario final en su bandeja de entrada (Gmail, Outlook, etc.).

> 🖼️ **[PEGAR AQUÍ CAPTURA 5: Captura del correo recibido con asunto "Welcome to our platform" y el saludo personalizado]**

---

### 📸 Evidencia 6: Registro de Envíos en el Dashboard de Brevo
Muestra el historial transaccional en Brevo confirmando el correo como "Delivered" (Entregado).

> 🖼️ **[PEGAR AQUÍ CAPTURA 6: Dashboard de Brevo -> Sección "Transactional" -> "Statistics" o "Logs" mostrando el correo entregado]**

---

## 6. Conclusiones

- **Desacoplamiento efectivo:** El microservicio no depende directamente de la base de datos de usuarios ni de llamadas síncronas; todo funciona mediante eventos en RabbitMQ.
- **Resiliencia y tolerancia a fallos:** Si el servicio de notificaciones está temporalmente apagado o en mantenimiento, los eventos se quedan almacenados de forma segura en RabbitMQ y se procesan automáticamente cuando el servicio vuelve a encenderse.
- **Despliegue moderno en la nube:** La combinación de Docker, Render, CloudAMQP y Brevo permite una arquitectura distribuida profesional, escalable y 100% funcional en la nube.
