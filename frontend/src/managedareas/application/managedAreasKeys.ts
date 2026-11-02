/** Claves de caché del módulo `managedareas`. */
export const managedAreasKeys = {
  all: ['managedareas'] as const,
  list: (managerId: string) => ['managedareas', 'list', managerId] as const,
}
