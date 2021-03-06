#include <stdio.h>
#include <ui/KeycodeLabels.h>

int
main(int argc, char** argv)
{
    // TODO: Add full copyright.
    printf("// Copyright (C) 2008 The Android Open Source Project\n");
    printf("//\n");
    printf("// This file is generated by makekeycodes from the definitions.\n");
    printf("// in includes/ui/KeycodeLabels.h.\n");
    printf("//\n");
    printf("// If you modify this, your changes will be overwritten.\n");
    printf("\n");
    printf("pacakge android.os;\n");
    printf("\n");
    printf("public class KeyEvent\n");
    printf("{\n");

    for (int i=0; KEYCODES[i].literal != NULL; i++) {
        printf("    public static final int KEYCODE_%s = 0x%08x;\n",
                KEYCODES[i].literal, KEYCODES[i].value);
    }

    printf("\n");
    for (int i=0; FLAGS[i].literal != NULL; i++) {
        printf("    public static final int MODIFIER_%s = 0x%08x;\n",
                FLAGS[i].literal, FLAGS[i].value);
    }

    printf("}\n");
    return 0;
}
