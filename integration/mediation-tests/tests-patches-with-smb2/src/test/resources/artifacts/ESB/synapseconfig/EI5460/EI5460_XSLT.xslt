<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fn="http://www.w3.org/2005/xpath-functions" exclude-result-prefixes="fn">
     <xsl:output indent="yes" encoding="ISO-8859-1" cdata-section-elements="ServiceProviderName ServiceProviderID ServiceID"/>
    <xsl:strip-space elements="*"/>
    <xsl:param name="ServiceProviderNameVal"/>
    <xsl:param name="ServiceProviderIDVal"/>
    <xsl:param name="ServiceIDVal"/>
    <xsl:template match="/">
        <Message>
            <Response>
                <FromInfo>
                    <ServiceProvider>
                        <ServiceProviderName><xsl:value-of select="$ServiceProviderNameVal"/></ServiceProviderName>
                    </ServiceProvider>
                </FromInfo>
                <ToInfo>
                    <ServiceProvider>
                        <ServiceProviderID><xsl:value-of select="$ServiceProviderIDVal"/></ServiceProviderID>
                        <ServiceID><xsl:value-of select="$ServiceIDVal"/></ServiceID>
                    </ServiceProvider>
                </ToInfo>
            </Response>
        </Message>
    </xsl:template>
</xsl:stylesheet>