FROM maven:4.0.0-rc-5-eclipse-temurin-25

# Instalar ferramentas
RUN apt-get update && apt-get install -y \
    zsh \
    git \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Instalar Oh My Zsh
RUN sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended

# Instalar Powerlevel10k
RUN git clone --depth=1 https://github.com/romkatv/powerlevel10k.git /root/.powerlevel10k

# Configurar .zshrc
RUN echo 'export ZSH="/root/.oh-my-zsh"' >> /root/.zshrc && \
    echo 'ZSH_THEME="powerlevel10k/powerlevel10k"' >> /root/.zshrc && \
    echo 'plugins=(git docker mvn)' >> /root/.zshrc && \
    echo 'source $ZSH/oh-my-zsh.sh' >> /root/.zshrc && \
    echo 'source /root/.powerlevel10k/powerlevel10k.zsh-theme' >> /root/.zshrc

# Definir zsh como shell padrão
SHELL ["/bin/zsh", "-c"]

WORKDIR /app

# Copiar projeto
COPY . .

EXPOSE 8080

# Default: abrir shell (dev)
CMD ["zsh"]