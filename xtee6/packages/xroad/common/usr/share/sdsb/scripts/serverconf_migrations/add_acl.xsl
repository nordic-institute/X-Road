<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:strip-space elements="*"/>
    <xsl:output method="xml" indent="yes" />

    <!-- Identity transform, copies everything -->
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

    <!-- Creates a new acl element under client for each service. Does
         not take into account different versions of services! -->
    <xsl:template match="client">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <xsl:for-each select="wsdl/service">
                <acl>
                    <serviceCode><xsl:value-of select="serviceCode"/></serviceCode>
                    <xsl:for-each select="authorizedSubject">
                        <xsl:copy>
                            <xsl:apply-templates select="@*|node()"/>
                        </xsl:copy>
                    </xsl:for-each>
                </acl>
            </xsl:for-each>
        </xsl:copy>
    </xsl:template>

    <!-- Deletes authorizedSubjects under service element -->
    <xsl:template match="authorizedSubject"/>
</xsl:stylesheet>
