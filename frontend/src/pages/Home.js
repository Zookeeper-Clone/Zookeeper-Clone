import * as React from 'react';
import PropTypes from 'prop-types';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { createTheme } from '@mui/material/styles';
import { AppProvider } from '@toolpad/core/AppProvider';
import { DashboardLayout } from '@toolpad/core/DashboardLayout';
import { DemoProvider, useDemoRouter } from '@toolpad/core/internal';
import DashboardIcon from '@mui/icons-material/Dashboard';
import EditIcon from '@mui/icons-material/Edit';
import BarChartIcon from '@mui/icons-material/BarChart';
import ReadPage from './ReadPage';
import WritePage from './EditPage';
import MetricsPage from './MetricsPage';

const NAVIGATION = [
  {
    kind: 'header',
    title: 'Main items',
  },
  {
    segment: 'read',
    title: 'Read',
    icon: <DashboardIcon />,
  },
  {
    segment: 'write',
    title: 'Write',
    icon: <EditIcon />,
  },
  {
    segment: 'metrics',
    title: 'Metrics',
    icon: <BarChartIcon />,
  },
];

const demoTheme = createTheme({
  cssVariables: {
    colorSchemeSelector: 'data-toolpad-color-scheme',
  },
  colorSchemes: { light: true, dark: true },
  breakpoints: {
    values: {
      xs: 0,
      sm: 600,
      md: 600,
      lg: 1200,
      xl: 1536,
    },
  },
});

function DemoPageContent({ pathname }) {
  console.log(pathname)
  switch (pathname) {
    case '/read':
      return <ReadPage />;
    case '/write':
      return <WritePage />;
    case '/metrics':
      return <MetricsPage />;
    default:
      return <ReadPage/>;
  }
}

DemoPageContent.propTypes = {
  pathname: PropTypes.string.isRequired,
};

function Home(props) {
  const { window } = props;

  const router = useDemoRouter('read');

  const demoWindow = window !== undefined ? window() : undefined;

  return (
    <DemoProvider window={demoWindow}>
      <AppProvider
        navigation={NAVIGATION}
        router={router}
        theme={demoTheme}
        window={demoWindow}
        branding={{
          title : "Zookeeper",
          logo: <img src='/zookeeper.png'></img>
        }}
      >
        <DashboardLayout>
          <DemoPageContent pathname={router.pathname} />
        </DashboardLayout>
      </AppProvider>
    </DemoProvider>
  );
}


export default Home;
