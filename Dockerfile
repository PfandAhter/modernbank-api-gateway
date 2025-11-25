# 1. Adım: Temel Java imajını al (Java 17 kullanıyorsan)
# Eğer Java 21 kullanıyorsan: eclipse-temurin:21-jdk-alpine yazabilirsin.
FROM eclipse-temurin:17-jdk-alpine

# 2. Adım: Çalışma klasörünü ayarla
WORKDIR /app

# 3. Adım: Maven ile oluşturduğun jar dosyasını konteyner içine kopyala
# "target/*.jar" diyerek versiyon numarası değişse bile (örn: 0.0.1-SNAPSHOT) otomatik bulmasını sağlıyoruz.
COPY target/*.jar app.jar

# 4. Adım: Uygulamanın çalıştığı portu belirt (Bilgi amaçlı)
EXPOSE 8080

# 5. Adım: Uygulamayı başlat
ENTRYPOINT ["java", "-jar", "app.jar"]