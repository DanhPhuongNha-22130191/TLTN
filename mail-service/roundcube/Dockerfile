FROM roundcube/roundcubemail:1.7.1-apache

COPY roundcube-ssl.conf /etc/apache2/sites-available/roundcube-ssl.conf

RUN a2enmod ssl headers \
    && a2dissite default-ssl \
    && a2ensite roundcube-ssl
