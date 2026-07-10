import type { CategoriaDenuncia } from "./theme";

export type RootStackParamList = {
  Home: undefined;
  Denuncias: undefined;
  NovaDenuncia: { categoria?: CategoriaDenuncia } | undefined;
  Agendar: undefined;
};
