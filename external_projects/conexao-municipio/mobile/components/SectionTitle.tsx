import { StyleSheet, Text, View } from "react-native";

import { cores } from "../lib/theme";

export default function SectionTitle({
  children,
  sublinhado = true,
}: {
  children: string;
  sublinhado?: boolean;
}) {
  return (
    <View style={styles.wrapper}>
      <Text style={styles.texto}>{children}</Text>
      {sublinhado ? <View style={styles.sublinhado} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    alignSelf: "flex-start",
  },
  texto: {
    fontSize: 17,
    fontWeight: "700",
    color: cores.texto,
  },
  sublinhado: {
    marginTop: 6,
    width: 36,
    height: 3.5,
    borderRadius: 2,
    backgroundColor: cores.primaria,
  },
});
